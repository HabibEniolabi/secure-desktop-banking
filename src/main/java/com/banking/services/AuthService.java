package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.Employee;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.PasswordService;

/** Authentication is implemented without storing passwords in the UI or logs. */
public final class AuthService {
    private final EmployeeRepository employees;
    private final AuditService audit;

    public AuthService(EmployeeRepository employees, AuditService audit) {
        this.employees = employees;
        this.audit = audit;
    }

    public AuthenticatedEmployee login(String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isBlank()) {
            throw new SecurityException("Invalid email/username or password.");
        }
        Employee employee = employees.findByLogin(login).orElse(null);
        // A failure should produce the same outward message for unknown/inactive/bad password.
        boolean valid = employee != null && employee.active()
            && PasswordService.verify(password, employee.passwordHash());
        audit.loginAttempt(employee == null ? null : employee.id(), valid);
        if (!valid) throw new SecurityException("Invalid email/username or password.");
        return new AuthenticatedEmployee(employee.id(), employee.fullName(), employee.email(), employee.role());
    }
}
