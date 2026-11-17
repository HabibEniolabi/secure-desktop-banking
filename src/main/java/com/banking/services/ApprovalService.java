package com.banking.services;

import com.banking.models.ApprovalTicket;
import com.banking.models.AuthenticatedEmployee;
import com.banking.repositories.TransactionRepository;
import com.banking.security.Permission;
import java.util.List;
import java.util.Objects;

/** Supervisor decisions require a current, active SUPERVISOR role from the database. */
public final class ApprovalService {
    private final TransactionRepository transactions;
    private final EmployeeAccess access;

    public ApprovalService(TransactionRepository transactions, EmployeeAccess access) {
        this.transactions = Objects.requireNonNull(transactions);
        this.access = Objects.requireNonNull(access);
    }

    public List<ApprovalTicket> pending(AuthenticatedEmployee supervisor, int page, int size) {
        access.require(supervisor, Permission.APPROVE_RESTRICTED_TRANSACTIONS);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid approval page.");
        return transactions.pendingApprovals(page, size);
    }

    public ApprovalTicket decide(AuthenticatedEmployee supervisor, String approvalId, boolean approve) {
        access.require(supervisor, Permission.APPROVE_RESTRICTED_TRANSACTIONS);
        if (approvalId == null || approvalId.isBlank())
            throw new IllegalArgumentException("Select an approval request.");
        return transactions.decideApproval(supervisor.id(), approvalId.trim(), approve);
    }
}
