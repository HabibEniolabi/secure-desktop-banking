package com.banking.models;

import java.time.Instant;
import java.util.List;

/** Immutable operational summary. Monetary totals are integer minor units (kobo). */
public record ReportSnapshot(
    ReportPeriod period,
    long customerCount,
    long activeAccounts,
    long frozenAccounts,
    long creditMinor,
    long debitMinor,
    long transferMinor,
    long unresolvedComplaints,
    long pendingApprovals,
    List<TransactionRow> recentTransactions
) {
    public ReportSnapshot {
        recentTransactions = List.copyOf(recentTransactions);
    }

    public record TransactionRow(String reference, String type, long amountMinor,
                                 String sourceAccount, String destinationAccount,
                                 String employeeId, Instant createdAt) { }

    /** Never include credentials, passwords, connection URLs, or customer contact details. */
    public record AuditRow(String event, String actorId, String target, Instant createdAt) { }
}
