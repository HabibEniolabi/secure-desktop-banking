package com.banking.services;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.Employee;
import com.banking.models.Role;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.Authorization;
import com.banking.security.PasswordService;
import com.banking.security.Permission;
import com.banking.validators.InputValidator;
import java.util.List;
import java.util.Objects;

/** All administration permissions are re-checked against the database, not a UI role flag. */
public final class EmployeeManagementService {
    private final EmployeeRepository employees;
    private final AuditService audit;

    public EmployeeManagementService(EmployeeRepository employees, AuditService audit) {
        this.employees = Objects.requireNonNull(employees);
        this.audit = Objects.requireNonNull(audit);
    }

    public List<EmployeeSummary> search(AuthenticatedEmployee actor, String query, int page, int pageSize) {
        requireAdministrator(actor);
        if (page < 0 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid page size.");
        return employees.search(query == null ? "" : query.trim(), page, pageSize)
            .stream().map(EmployeeSummary::from).toList();
    }

    public EmployeeSummary create(AuthenticatedEmployee actor, String fullName, String email,
                                  String username, String password, Role role) {
        requireAdministrator(actor);
        String normalizedName = InputValidator.required(fullName, "Full name");
        String normalizedEmail = InputValidator.email(email);
        String normalizedUsername = InputValidator.required(username, "Username").toLowerCase(java.util.Locale.ROOT);
        if (!normalizedUsername.matches("[a-z0-9._-]{3,32}")) {
            throw new IllegalArgumentException("Username must be 3–32 characters (letters, digits, . _ -).");
        }
        if (role == null) throw new IllegalArgumentException("Role is required.");
        String hash = PasswordService.hash(password);
        Employee created = employees.create(normalizedName, normalizedEmail, normalizedUsername, hash, role);
        audit.employeeChange(actor.id(), "EMPLOYEE_CREATED", created.id());
        return EmployeeSummary.from(created);
    }

    public EmployeeSummary update(AuthenticatedEmployee actor, String targetId, String fullName,
                                  String email, Role role, boolean active) {
        requireAdministrator(actor);
        String normalizedName = InputValidator.required(fullName, "Full name");
        String normalizedEmail = InputValidator.email(email);
        if (role == null) throw new IllegalArgumentException("Role is required.");
        Employee previous = employees.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found."));
        if (actor.id().equals(targetId) && (!active || role != Role.ADMINISTRATOR)) {
            throw new IllegalArgumentException("You cannot revoke your own administrator access.");
        }
        if (previous.role() == Role.ADMINISTRATOR && previous.active() &&
            (!active || role != Role.ADMINISTRATOR) && employees.countActiveAdministrators() <= 1) {
            throw new IllegalArgumentException("At least one active administrator is required.");
        }
        Employee changed = employees.update(targetId, normalizedName, normalizedEmail, role, active)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found or changed concurrently."));
        audit.employeeChange(actor.id(), "EMPLOYEE_UPDATED", changed.id());
        return EmployeeSummary.from(changed);
    }

    private void requireAdministrator(AuthenticatedEmployee actor) {
        if (actor == null) throw new SecurityException("Authentication required.");
        Employee current = employees.findById(actor.id())
            .orElseThrow(() -> new SecurityException("Employee session is no longer valid."));
        if (!current.active()) throw new SecurityException("Employee account is disabled.");
        Authorization.require(current.role(), Permission.MANAGE_EMPLOYEES);
    }

    /** Safe UI projection: never expose or log password hashes. */
    public record EmployeeSummary(String id, String fullName, String email, String username,
                                  Role role, boolean active) {
        static EmployeeSummary from(Employee e) {
            return new EmployeeSummary(e.id(), e.fullName(), e.email(), e.username(), e.role(), e.active());
        }
    }
}
