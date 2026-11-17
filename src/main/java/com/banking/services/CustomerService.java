package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.Customer;
import com.banking.repositories.CustomerRepository;
import com.banking.security.Permission;
import com.banking.validators.CustomerValidator;
import com.banking.validators.CustomerValidator.CustomerData;
import java.time.LocalDate;
import java.util.List;

public final class CustomerService {
    private final CustomerRepository customers;
    private final EmployeeAccess access;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, EmployeeAccess access, AuditService audit) {
        this.customers = customers; this.access = access; this.audit = audit;
    }

    public Customer register(AuthenticatedEmployee actor, String name, String email, String phone,
                             String address, LocalDate birthDate) {
        access.require(actor, Permission.MANAGE_CUSTOMERS);
        CustomerData data = CustomerValidator.validate(name, email, phone, address, birthDate);
        Customer created = customers.create(data);
        audit.activity(actor.id(), "CUSTOMER_CREATED", "customerId", created.customerId());
        return created;
    }

    public Customer update(AuthenticatedEmployee actor, String customerId, String name, String email,
                           String phone, String address, LocalDate birthDate) {
        access.require(actor, Permission.MANAGE_CUSTOMERS);
        CustomerData data = CustomerValidator.validate(name, email, phone, address, birthDate);
        Customer changed = customers.update(customerId, data)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        audit.activity(actor.id(), "CUSTOMER_UPDATED", "customerId", customerId);
        return changed;
    }

    public List<Customer> search(AuthenticatedEmployee actor, String query, int page, int size) {
        access.require(actor, Permission.VIEW_CUSTOMERS);
        validatePage(page, size);
        return customers.search(query, page, size);
    }

    public Customer byId(AuthenticatedEmployee actor, String id) {
        access.require(actor, Permission.VIEW_CUSTOMERS);
        return customers.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long)page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page or page size.");
    }
}
