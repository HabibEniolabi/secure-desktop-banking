package com.banking.repositories;

import com.banking.models.AccountStatus;
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

    public TransactionRepository(MongoClient client, MongoDatabase database) {
        this.client = Objects.requireNonNull(client);
        Objects.requireNonNull(database);
        this.accounts = database.getCollection("accounts");
        this.transactions = database.getCollection("transactions");
        this.ledger = database.getCollection("ledger_entries");
        this.audit = database.getCollection("audit_logs");
    }

    public TransactionReceipt post(String employeeId, BankingRequest request, long limitMinor) {
        if (employeeId == null || employeeId.isBlank()) throw new SecurityException("Sign in to continue.");
        Objects.requireNonNull(request);
        if (limitMinor <= 0) throw new IllegalArgumentException("Approval limit is not configured.");

        final String reference = "TXN-" + UUID.randomUUID();
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> postInsideTransaction(session, employeeId, request,
                limitMinor, reference), TX_OPTIONS);
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
                                                      String reference) {
        Document already = transactions.find(session, eq("requestId", request.requestId())).first();
        if (already != null) return matchedExisting(already, employeeId, request);
        if (request.amountMinor() > limitMinor) {
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
        // No SMTP call here: PR #7 will schedule notifications after the commit.
        return map(record);
    }

    private static Document ledgerEntry(String reference, String requestId, String accountNumber,
                                        String employeeId, String direction, long deltaMinor,
                                        Long balanceAfterMinor, Date when) {
        return new Document("reference", reference).append("requestId", requestId)
            .append("accountNumber", accountNumber).append("direction", direction)
            .append("deltaMinor", deltaMinor).append("balanceAfterMinor", balanceAfterMinor)
            .append("employeeId", employeeId).append("createdAt", when);
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
