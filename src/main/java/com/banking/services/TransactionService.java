package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.BankingRequest;
import com.banking.models.TransactionReceipt;
import com.banking.models.TransactionType;
import com.banking.repositories.TransactionRepository;
import com.banking.security.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Server-side role and amount checks; delegates all balance mutations to one transactional repository. */
public final class TransactionService {
    private final TransactionRepository repository;
    private final EmployeeAccess access;
    private final PostingPolicy policy;

    public TransactionService(TransactionRepository repository, EmployeeAccess access, PostingPolicy policy) {
        this.repository = Objects.requireNonNull(repository);
        this.access = Objects.requireNonNull(access);
        this.policy = Objects.requireNonNull(policy);
    }

    public TransactionReceipt post(AuthenticatedEmployee actor, BankingRequest request) {
        Objects.requireNonNull(request, "request");
        Permission permission = switch (request.type()) {
            case CREDIT -> Permission.CREDIT;
            case DEBIT -> Permission.DEBIT;
            case TRANSFER -> Permission.TRANSFER;
        };
        access.require(actor, permission);
        // Requests already posted must be retrievable even if policy subsequently changes.
        // The repository first checks this unique requestId atomically inside the transaction.
        return repository.post(actor.id(), request, policy.tellerLimitMinor());
    }

    public List<TransactionReceipt> history(AuthenticatedEmployee actor, String accountNumber,
                                            TransactionType type, Instant since, Instant until,
                                            int page, int pageSize) {
        access.require(actor, Permission.VIEW_TRANSACTIONS);
        if (page < 0 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid page size.");
        if (since != null && until != null && since.isAfter(until))
            throw new IllegalArgumentException("Start date cannot be after end date.");
        return repository.history(accountNumber, type, since, until, page, pageSize);
    }
}
