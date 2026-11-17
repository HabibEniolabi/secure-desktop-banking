package com.banking.repositories;

import com.banking.models.AccountStatus;
import com.banking.notifications.NotificationOutbox;
import com.banking.models.ApprovalTicket;
import com.banking.models.Role;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.mongodb.MongoWriteException;
import com.mongodb.ErrorCategory;
import com.banking.models.BankingRequest;
import com.banking.models.TransactionReceipt;
import com.banking.models.TransactionType;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/**
 * Financial consistency boundary. Account changes, transaction record, ledger entries and audit
 * are committed as ONE MongoDB multi-document transaction on an Atlas replica set/sharded cluster.
 * Never read a balance into Java and overwrite it; use conditional $inc updates.
 */
public final class TransactionRepository {
    private static final FindOneAndUpdateOptions UPDATED =
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
    private static final TransactionOptions TX_OPTIONS = TransactionOptions.builder()
        .readConcern(ReadConcern.SNAPSHOT)
        .writeConcern(WriteConcern.MAJORITY)
        .readPreference(ReadPreference.primary())
        .build();

    private final MongoClient client;
    private final MongoCollection<Document> accounts;
    private final MongoCollection<Document> transactions;
    private final MongoCollection<Document> ledger;
    private final MongoCollection<Document> audit;
    private final MongoCollection<Document> approvals;
    private final MongoCollection<Document> employees;
    private final MongoCollection<Document> customers;
    private final MongoCollection<Document> notifications;

    public TransactionRepository(MongoClient client, MongoDatabase database) {
        this.client = Objects.requireNonNull(client);
        Objects.requireNonNull(database);
        this.accounts = database.getCollection("accounts");
        this.transactions = database.getCollection("transactions");
        this.ledger = database.getCollection("ledger_entries");
        this.audit = database.getCollection("audit_logs");
        this.approvals = database.getCollection("approvals");
        this.employees = database.getCollection("employees");
        this.customers = database.getCollection("customers");
        this.notifications = database.getCollection("email_notifications");
    }

    public TransactionReceipt post(String employeeId, BankingRequest request, long limitMinor) {
        if (employeeId == null || employeeId.isBlank()) throw new SecurityException("Sign in to continue.");
        Objects.requireNonNull(request);
        if (limitMinor <= 0) throw new IllegalArgumentException("Approval limit is not configured.");

        final String reference = "TXN-" + UUID.randomUUID();
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> postInsideTransaction(session, employeeId, request,
                limitMinor, reference, false), TX_OPTIONS);
        } catch (MongoException ex) {
            // Network failure / unknown commit outcome: NEVER blindly repeat a financial request
            // with a new ID. Check the original ID first, and ask UI to retain it if unresolved.
            try {
                Document committed = transactions.find(eq("requestId", request.requestId())).first();
                if (committed != null) return matchedExisting(committed, employeeId, request);
            } catch (MongoException retryLookupFailure) {
                throw new OutcomeUnknownException(request.requestId(), retryLookupFailure);
            }
            throw new OutcomeUnknownException(request.requestId(), ex);
        }
    }

    private TransactionReceipt postInsideTransaction(ClientSession session, String employeeId,
                                                      BankingRequest request, long limitMinor,
                                                      String reference, boolean approvedExecution) {
        Document already = transactions.find(session, eq("requestId", request.requestId())).first();
        if (already != null) return matchedExisting(already, employeeId, request);
        // Once queued for approval, even a changed policy must not bypass the supervisor.
        if (!approvedExecution && approvals.find(session, eq("requestId", request.requestId())).first() != null) {
            throw new IllegalArgumentException("This request is already in the supervisor approval workflow.");
        }
        if (!approvedExecution && request.amountMinor() > limitMinor) {
            throw new IllegalArgumentException("This amount requires supervisor approval. " +
                "Approval workflow is not available yet; no funds have moved.");
        }

        Long fromBalance = null;
        Long toBalance = null;
        switch (request.type()) {
            case CREDIT -> {
                // Also prevent overflow when the balance is close to Long.MAX_VALUE.
                Document updated = accounts.findOneAndUpdate(session,
                    and(eq("accountNumber", request.destinationAccountNumber()),
                        eq("status", AccountStatus.ACTIVE.name()),
                        lte("balanceMinor", Long.MAX_VALUE - request.amountMinor())),
                    combine(inc("balanceMinor", request.amountMinor()), set("updatedAt", new Date())), UPDATED);
                if (updated == null) throw creditFailure(session, request.destinationAccountNumber());
                toBalance = updated.getLong("balanceMinor");
            }
            case DEBIT -> {
                Document updated = accounts.findOneAndUpdate(session,
                    and(eq("accountNumber", request.sourceAccountNumber()),
                        eq("status", AccountStatus.ACTIVE.name()), gte("balanceMinor", request.amountMinor())),
                    combine(inc("balanceMinor", -request.amountMinor()), set("updatedAt", new Date())), UPDATED);
                if (updated == null) throw debitFailure(session, request.sourceAccountNumber());
                fromBalance = updated.getLong("balanceMinor");
            }
            case TRANSFER -> {
                Document debited = accounts.findOneAndUpdate(session,
                    and(eq("accountNumber", request.sourceAccountNumber()),
                        eq("status", AccountStatus.ACTIVE.name()), gte("balanceMinor", request.amountMinor())),
                    combine(inc("balanceMinor", -request.amountMinor()), set("updatedAt", new Date())), UPDATED);
                if (debited == null) throw debitFailure(session, request.sourceAccountNumber());
                // If the destination update fails, MongoDB aborts the entire transaction,
                // including this source debit: money cannot disappear between accounts.
                Document credited = accounts.findOneAndUpdate(session,
                    and(eq("accountNumber", request.destinationAccountNumber()),
                        eq("status", AccountStatus.ACTIVE.name()),
                        lte("balanceMinor", Long.MAX_VALUE - request.amountMinor())),
                    combine(inc("balanceMinor", request.amountMinor()), set("updatedAt", new Date())), UPDATED);
                if (credited == null) throw creditFailure(session, request.destinationAccountNumber());
                fromBalance = debited.getLong("balanceMinor");
                toBalance = credited.getLong("balanceMinor");
            }
        }

        Date when = new Date();
        Document record = new Document("reference", reference)
            .append("requestId", request.requestId())
            .append("type", request.type().name())
            .append("status", "COMPLETED")
            .append("amountMinor", request.amountMinor())
            .append("sourceAccountNumber", request.sourceAccountNumber())
            .append("destinationAccountNumber", request.destinationAccountNumber())
            // Used by the existing history index for one-account operations.
            .append("accountNumber", request.type() == TransactionType.CREDIT
                ? request.destinationAccountNumber() : request.sourceAccountNumber())
            .append("sourceBalanceAfterMinor", fromBalance)
            .append("destinationBalanceAfterMinor", toBalance)
            .append("employeeId", employeeId)
            .append("createdAt", when);
        transactions.insertOne(session, record);
        if (request.sourceAccountNumber() != null) ledger.insertOne(session, ledgerEntry(
            reference, request.requestId(), request.sourceAccountNumber(), employeeId,
            "DEBIT", -request.amountMinor(), fromBalance, when));
        if (request.destinationAccountNumber() != null) ledger.insertOne(session, ledgerEntry(
            reference, request.requestId(), request.destinationAccountNumber(), employeeId,
            "CREDIT", request.amountMinor(), toBalance, when));
        audit.insertOne(session, new Document("event", "FINANCIAL_" + request.type().name())
            .append("employeeId", employeeId).append("reference", reference)
            .append("requestId", request.requestId()).append("createdAt", when));
        // The outbox is written in the same ACID transaction as the balances and ledger.
        // SMTP is only attempted later by NotificationWorker, after this transaction commits.
        if (request.sourceAccountNumber() != null) {
            queueTransactionNotice(session, request.type() == TransactionType.DEBIT ? "DEBIT" : "TRANSFER_SENT",
                request.sourceAccountNumber(), request.amountMinor(), reference, when);
        }
        if (request.destinationAccountNumber() != null) {
            queueTransactionNotice(session, request.type() == TransactionType.CREDIT ? "CREDIT" : "TRANSFER_RECEIVED",
                request.destinationAccountNumber(), request.amountMinor(), reference, when);
        }
        return map(record);
    }

    private void queueTransactionNotice(ClientSession session, String event, String accountNumber,
                                        long amountMinor, String reference, Date when) {
        Document account = accounts.find(session, eq("accountNumber", accountNumber)).first();
        if (account == null) throw new IllegalStateException("Account disappeared during posting.");
        Document customer = customers.find(session, eq("customerId", account.getString("customerId"))).first();
        if (customer == null) throw new IllegalStateException("Customer was not found for notification.");
        notifications.insertOne(session, NotificationOutbox.financial(event, customer.getString("email"),
            accountNumber, amountMinor, reference, when));
    }

    private static Document ledgerEntry(String reference, String requestId, String accountNumber,
                                        String employeeId, String direction, long deltaMinor,
                                        Long balanceAfterMinor, Date when) {
        return new Document("reference", reference).append("requestId", requestId)
            .append("accountNumber", accountNumber).append("direction", direction)
            .append("deltaMinor", deltaMinor).append("balanceAfterMinor", balanceAfterMinor)
            .append("employeeId", employeeId).append("createdAt", when);
    }

    /** Record a high-value request without touching either account's balance. */
    public ApprovalTicket submitForApproval(String employeeId, BankingRequest request, long limitMinor) {
        if (employeeId == null || employeeId.isBlank()) throw new SecurityException("Sign in to continue.");
        Objects.requireNonNull(request);
        if (limitMinor <= 0 || request.amountMinor() <= limitMinor)
            throw new IllegalArgumentException("Only restricted transactions enter supervisor approval.");
        if (transactions.find(eq("requestId", request.requestId())).first() != null)
            throw new IllegalArgumentException("This request ID already belongs to a completed transaction.");
        Date now = new Date();
        Document item = new Document("approvalId", "APR-" + UUID.randomUUID())
            .append("requestId", request.requestId())
            .append("type", request.type().name())
            .append("sourceAccountNumber", request.sourceAccountNumber())
            .append("destinationAccountNumber", request.destinationAccountNumber())
            .append("amountMinor", request.amountMinor())
            .append("initiatedBy", employeeId)
            .append("status", "PENDING")
            .append("createdAt", now);
        try {
            approvals.insertOne(item);
            return mapApproval(item);
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw ex;
            Document existing = approvals.find(eq("requestId", request.requestId())).first();
            if (existing == null) throw ex;
            if (!employeeId.equals(existing.getString("initiatedBy"))
                || request.amountMinor() != existing.getLong("amountMinor")
                || !request.type().name().equals(existing.getString("type"))
                || !Objects.equals(request.sourceAccountNumber(), existing.getString("sourceAccountNumber"))
                || !Objects.equals(request.destinationAccountNumber(), existing.getString("destinationAccountNumber"))) {
                throw new IllegalArgumentException("Request ID belongs to a different approval request.");
            }
            return mapApproval(existing); // stable ID when submission is retried
        }
    }

    public List<ApprovalTicket> pendingApprovals(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid approval page.");
        List<ApprovalTicket> found = new ArrayList<>();
        approvals.find(eq("status", "PENDING"))
            .sort(Sorts.orderBy(Sorts.ascending("createdAt"), Sorts.ascending("approvalId")))
            .skip(page * size).limit(size)
            .forEach(item -> found.add(mapApproval(item)));
        return found;
    }

    /**
     * The approval decision and (if approved) the money movement are in ONE DB transaction.
     * A failed debit/credit, revoked teller, or closed account rolls back the approval too.
     * Two supervisors cannot execute the same approval twice.
     */
    public ApprovalTicket decideApproval(String supervisorId, String approvalId, boolean approve) {
        if (supervisorId == null || supervisorId.isBlank()) throw new SecurityException("Sign in to continue.");
        String reference = "TXN-" + UUID.randomUUID();
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                Document supervisor = employees.find(session, eq("employeeId", supervisorId)).first();
                if (supervisor == null || !Boolean.TRUE.equals(supervisor.getBoolean("active"))
                    || !Role.SUPERVISOR.name().equals(supervisor.getString("role")))
                    throw new SecurityException("Only active supervisors may decide approvals.");

                Document item = approvals.find(session, eq("approvalId", approvalId)).first();
                if (item == null) throw new IllegalArgumentException("Approval request does not exist.");
                if (!"PENDING".equals(item.getString("status")))
                    throw new IllegalArgumentException("This approval has already been decided.");
                String initiatedBy = item.getString("initiatedBy");
                if (supervisorId.equals(initiatedBy))
                    throw new SecurityException("You cannot approve or reject your own request.");

                String transactionReference = null;
                if (approve) {
                    Document initiator = employees.find(session, eq("employeeId", initiatedBy)).first();
                    if (initiator == null || !Boolean.TRUE.equals(initiator.getBoolean("active")))
                        throw new SecurityException("The initiating employee is no longer active.");
                    BankingRequest request = new BankingRequest(item.getString("requestId"),
                        TransactionType.valueOf(item.getString("type")),
                        item.getString("sourceAccountNumber"), item.getString("destinationAccountNumber"),
                        item.getLong("amountMinor"));
                    Permission permission = switch (request.type()) {
                        case CREDIT -> Permission.CREDIT;
                        case DEBIT -> Permission.DEBIT;
                        case TRANSFER -> Permission.TRANSFER;
                    };
                    Authorization.require(Role.valueOf(initiator.getString("role")), permission);
                    if (transactions.find(session, eq("requestId", request.requestId())).first() != null)
                        throw new IllegalArgumentException("This request ID has already been processed.");
                    // Exempt ONLY this supervisor-authorized execution from the configured limit.
                    TransactionReceipt completed = postInsideTransaction(session, initiatedBy,
                        request, Long.MAX_VALUE, reference, true);
                    transactionReference = completed.reference();
                }
                Date now = new Date();
                Document changed = approvals.findOneAndUpdate(session,
                    and(eq("approvalId", approvalId), eq("status", "PENDING")),
                    combine(set("status", approve ? "APPROVED" : "REJECTED"),
                        set("reviewedBy", supervisorId), set("decidedAt", now),
                        set("transactionReference", transactionReference)), UPDATED);
                if (changed == null) throw new IllegalStateException("Approval was reviewed concurrently.");
                audit.insertOne(session, new Document("event", approve ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED")
                    .append("employeeId", supervisorId).append("approvalId", approvalId)
                    .append("requestId", item.getString("requestId"))
                    .append("createdAt", now));
                return mapApproval(changed);
            }, TX_OPTIONS);
        }
    }

    private static ApprovalTicket mapApproval(Document d) {
        Date decidedAt = d.getDate("decidedAt");
        return new ApprovalTicket(d.getString("approvalId"), d.getString("requestId"),
            TransactionType.valueOf(d.getString("type")), d.getString("sourceAccountNumber"),
            d.getString("destinationAccountNumber"), d.getLong("amountMinor"),
            d.getString("initiatedBy"), d.getString("status"),
            d.getString("reviewedBy"), d.getString("transactionReference"),
            d.getDate("createdAt").toInstant(), decidedAt == null ? null : decidedAt.toInstant());
    }

    /** Mismatched payload on a reused key must never return another operation's receipt. */
    private static TransactionReceipt matchedExisting(Document d, String actorId, BankingRequest command) {
        if (!actorId.equals(d.getString("employeeId"))
            || !command.type().name().equals(d.getString("type"))
            || command.amountMinor() != d.getLong("amountMinor")
            || !Objects.equals(command.sourceAccountNumber(), d.getString("sourceAccountNumber"))
            || !Objects.equals(command.destinationAccountNumber(), d.getString("destinationAccountNumber"))) {
            throw new IllegalArgumentException("Request ID has already been used for a different transaction.");
        }
        return map(d);
    }

    private IllegalArgumentException debitFailure(ClientSession session, String accountNumber) {
        Document account = accounts.find(session, eq("accountNumber", accountNumber)).first();
        if (account == null) return new IllegalArgumentException("Source account does not exist.");
        if (!AccountStatus.ACTIVE.name().equals(account.getString("status")))
            return new IllegalArgumentException("Source account is not active.");
        return new IllegalArgumentException("Insufficient funds for the requested debit.");
    }

    private IllegalArgumentException creditFailure(ClientSession session, String accountNumber) {
        Document account = accounts.find(session, eq("accountNumber", accountNumber)).first();
        if (account == null) return new IllegalArgumentException("Destination account does not exist.");
        if (!AccountStatus.ACTIVE.name().equals(account.getString("status")))
            return new IllegalArgumentException("Destination account is not active.");
        return new IllegalArgumentException("Credit exceeds the maximum supported account balance.");
    }

    public List<TransactionReceipt> history(String accountNumber, TransactionType type, Instant since,
                                             Instant until, int page, int size) {
        List<Bson> conditions = new ArrayList<>();
        if (accountNumber != null && !accountNumber.isBlank()) {
            String number = accountNumber.trim();
            if (!number.matches("[0-9]{10}")) throw new IllegalArgumentException("Enter a 10-digit account number.");
            conditions.add(or(eq("sourceAccountNumber", number), eq("destinationAccountNumber", number)));
        }
        if (type != null) conditions.add(eq("type", type.name()));
        if (since != null) conditions.add(gte("createdAt", Date.from(since)));
        if (until != null) conditions.add(lte("createdAt", Date.from(until)));
        Bson filter = conditions.isEmpty() ? new Document() : and(conditions);
        List<TransactionReceipt> results = new ArrayList<>();
        transactions.find(filter).sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.ascending("reference")))
            .skip(Math.multiplyExact(page, size)).limit(size).forEach(d -> results.add(map(d)));
        return results;
    }

    private static TransactionReceipt map(Document d) {
        return new TransactionReceipt(d.getString("reference"), d.getString("requestId"),
            TransactionType.valueOf(d.getString("type")), d.getString("sourceAccountNumber"),
            d.getString("destinationAccountNumber"), d.getLong("amountMinor"),
            d.getLong("sourceBalanceAfterMinor"), d.getLong("destinationBalanceAfterMinor"),
            d.getString("employeeId"), d.getDate("createdAt").toInstant());
    }

    /** A network error prevented confirming success or failure; do not generate a new request ID. */
    public static final class OutcomeUnknownException extends RuntimeException {
        private final String requestId;
        public OutcomeUnknownException(String requestId, Throwable cause) {
            super("Transaction outcome is unknown. Retry with the SAME request ID: " + requestId, cause);
            this.requestId = requestId;
        }
        public String requestId() { return requestId; }
    }
}
