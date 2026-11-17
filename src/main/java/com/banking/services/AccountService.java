package com.banking.services;

import com.banking.models.AccountStatus;
import com.banking.models.AuthenticatedEmployee;
import com.banking.models.BankAccount;
import com.banking.repositories.AccountRepository;
import com.banking.repositories.CustomerRepository;
import com.banking.security.Permission;
import java.security.SecureRandom;
import java.util.List;

public final class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final EmployeeAccess access;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, CustomerRepository customers,
                          EmployeeAccess access, AuditService audit) {
        this.accounts = accounts; this.customers = customers; this.access = access; this.audit = audit;
    }

    public BankAccount open(AuthenticatedEmployee actor, String customerId) {
        access.require(actor, Permission.CREATE_ACCOUNTS);
        if (customerId == null || customerId.isBlank() || customers.findById(customerId).isEmpty())
            throw new IllegalArgumentException("Register or select a customer first.");
        for (int attempts = 0; attempts < 8; attempts++) {
            // 10-digit numerical account number; the unique MongoDB index handles rare collisions.
            String number = Long.toString(1_000_000_000L + RANDOM.nextLong(9_000_000_000L));
            try {
                BankAccount created = accounts.create(customerId, number);
                audit.activity(actor.id(), "ACCOUNT_CREATED", "accountNumber", created.accountNumber());
                return created;
            } catch (AccountRepository.AccountNumberCollisionException ignored) {
                // Generate another number instead of showing the user a database duplicate-key error.
            }
        }
        throw new IllegalStateException("Could not generate an unused account number. Retry.");
    }

    public List<BankAccount> byCustomer(AuthenticatedEmployee actor, String customerId, int page, int size) {
        access.require(actor, Permission.VIEW_ACCOUNTS);
        CustomerService.validatePage(page, size);
        return accounts.byCustomer(customerId, page, size);
    }

    public BankAccount byNumber(AuthenticatedEmployee actor, String accountNumber) {
        access.require(actor, Permission.VIEW_ACCOUNTS);
        return accounts.findByNumber(accountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    public BankAccount changeStatus(AuthenticatedEmployee actor, String accountNumber, AccountStatus status) {
        access.require(actor, Permission.MANAGE_ACCOUNT_STATUS);
        if (status == null) throw new IllegalArgumentException("Account status is required.");
        BankAccount updated = accounts.changeStatus(accountNumber, status)
            .orElseThrow(() -> new IllegalArgumentException(
                "Account missing, already closed, or non-zero balance prevents closing."));
        audit.activity(actor.id(), "ACCOUNT_STATUS_CHANGED", "accountNumber", accountNumber);
        return updated;
    }
}
