package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.Employee;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.Authorization;
import com.banking.security.Permission;

/** Always resolve current permissions from MongoDB, never trust a cached UI role. */
public final class EmployeeAccess {
    private final EmployeeRepository employees;
    public EmployeeAccess(EmployeeRepository employees) { this.employees = employees; }

    public void require(AuthenticatedEmployee actor, Permission permission) {
        if (actor == null) throw new SecurityException("Sign in to continue.");
        Employee current = employees.findById(actor.id())
            .orElseThrow(() -> new SecurityException("Employee session is no longer valid."));
        if (!current.active()) throw new SecurityException("This employee is inactive.");
        Authorization.require(current.role(), permission);
    }
}
