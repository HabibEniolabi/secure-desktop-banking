package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.ReportPeriod;
import com.banking.models.ReportSnapshot;
import com.banking.models.ReportSnapshot.AuditRow;
import com.banking.models.ReportSnapshot.TransactionRow;
import com.banking.security.Permission;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.mongodb.client.model.Filters.*;

/** Read-only, bounded aggregation/reporting operations. No full-collection materialization. */
public final class ReportService {
    private final MongoCollection<Document> customers;
    private final MongoCollection<Document> accounts;
    private final MongoCollection<Document> transactions;
    private final MongoCollection<Document> tickets;
    private final MongoCollection<Document> approvals;
    private final MongoCollection<Document> audit;
    private final EmployeeAccess access;

    public ReportService(MongoDatabase database, EmployeeAccess access) {
        Objects.requireNonNull(database, "Database required.");
        this.access = Objects.requireNonNull(access, "Access checker required.");
        customers = database.getCollection("customers");
        accounts = database.getCollection("accounts");
        transactions = database.getCollection("transactions");
        tickets = database.getCollection("support_tickets");
        approvals = database.getCollection("approvals");
        audit = database.getCollection("audit_logs");
    }

    public ReportSnapshot load(AuthenticatedEmployee actor, ReportPeriod period) {
        access.require(actor, Permission.VIEW_REPORTS);
        Objects.requireNonNull(period, "Date range required.");

        // These are live snapshot counts, not counts of records created during the selected dates.
        long totalCustomers = customers.countDocuments();
        long activeAccounts = accounts.countDocuments(eq("status", "ACTIVE"));
        long frozenAccounts = accounts.countDocuments(eq("status", "FROZEN"));
        long unresolved = tickets.countDocuments(in("status", "OPEN", "IN_PROGRESS", "ESCALATED"));
        long pending = approvals.countDocuments(eq("status", "PENDING"));

        Bson selectedTransactions = and(
            eq("status", "COMPLETED"),
            gte("createdAt", Date.from(period.startInclusive())),
            lt("createdAt", Date.from(period.endExclusive())));
        long credits = 0L, debits = 0L, transfers = 0L;
        for (Document row : transactions.aggregate(List.of(
            Aggregates.match(selectedTransactions),
            Aggregates.group("$type", Accumulators.sum("totalMinor", "$amountMinor"))))) {
            String type = row.getString("_id");
            long minor = exactMinor(row.get("totalMinor"));
            if ("CREDIT".equals(type)) credits = minor;
            else if ("DEBIT".equals(type)) debits = minor;
            else if ("TRANSFER".equals(type)) transfers = minor;
        }

        List<TransactionRow> recent = new ArrayList<>();
        transactions.find(selectedTransactions)
            .sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.ascending("reference")))
            .limit(30).forEach(doc -> recent.add(new TransactionRow(
                doc.getString("reference"), doc.getString("type"), longValue(doc, "amountMinor"),
                doc.getString("sourceAccountNumber"), doc.getString("destinationAccountNumber"),
                doc.getString("employeeId"), instant(doc, "createdAt"))));

        return new ReportSnapshot(period, totalCustomers, activeAccounts, frozenAccounts,
            credits, debits, transfers, unresolved, pending, recent);
    }

    /** Administrator-only audit display, with a bounded and date-filtered query. */
    public List<AuditRow> recentAudit(AuthenticatedEmployee actor, ReportPeriod period) {
        access.require(actor, Permission.VIEW_AUDIT_LOGS);
        Objects.requireNonNull(period, "Date range required.");
        Bson dates = and(gte("createdAt", Date.from(period.startInclusive())),
            lt("createdAt", Date.from(period.endExclusive())));
        List<AuditRow> result = new ArrayList<>();
        audit.find(dates).sort(Sorts.descending("createdAt")).limit(30).forEach(doc -> {
            String target = firstNonBlank(doc, "reference", "ticketNumber", "approvalId",
                "targetEmployeeId", "accountNumber", "customerId");
            result.add(new AuditRow(doc.getString("event"), doc.getString("employeeId"),
                target == null ? "—" : target, instant(doc, "createdAt")));
        });
        return List.copyOf(result);
    }

    private static String firstNonBlank(Document document, String... fields) {
        for (String field : fields) {
            String value = document.getString(field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static Instant instant(Document document, String field) {
        Date value = document.getDate(field);
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static long longValue(Document document, String field) {
        Object raw = document.get(field);
        return raw == null ? 0L : exactMinor(raw);
    }

    private static long exactMinor(Object raw) {
        if (raw instanceof Long l) return l;
        if (raw instanceof Integer i) return i.longValue();
        if (raw instanceof Decimal128 d) return d.bigDecimalValue().longValueExact();
        if (raw instanceof Number n) {
            double number = n.doubleValue();
            if (!Double.isFinite(number) || number != Math.rint(number)
                || number >= 0x1.0p63 || number < -0x1.0p63)
                throw new IllegalStateException("Report total exceeds supported exact currency range.");
            return n.longValue();
        }
        throw new IllegalStateException("Unsupported MongoDB report amount type.");
    }
}
