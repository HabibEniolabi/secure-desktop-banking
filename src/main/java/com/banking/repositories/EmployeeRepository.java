package com.banking.repositories;

import com.banking.models.Employee;
import com.banking.models.Role;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import org.bson.Document;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class EmployeeRepository {
    private final MongoCollection<Document> employees;

    public EmployeeRepository(MongoDatabase database) {
        this.employees = database.getCollection("employees");
    }

    public Optional<Employee> findByLogin(String login) {
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        var doc = employees.find(or(eq("email", normalized), eq("username", normalized))).first();
        return Optional.ofNullable(doc).map(this::map);
    }

    public Optional<Employee> findByEmail(String email) {
        return Optional.ofNullable(employees.find(eq("email", email.toLowerCase(Locale.ROOT))).first()).map(this::map);
    }

    /** Only called by the explicit bootstrap CLI; never accepts a role from an untrusted form. */
    public void seedAdministrator(String fullName, String email, String passwordHash) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("An account already exists with that seed email.");
        }
        String employeeId = UUID.randomUUID().toString();
        employees.insertOne(new Document("employeeId", employeeId)
            .append("fullName", fullName.trim())
            .append("email", normalizedEmail)
            .append("username", normalizedEmail)
            .append("passwordHash", passwordHash)
            .append("role", Role.ADMINISTRATOR.name())
            .append("active", true)
            .append("createdAt", java.util.Date.from(Instant.now())));
    }

    private Employee map(Document doc) {
        return new Employee(doc.getString("employeeId"), doc.getString("fullName"),
            doc.getString("email"), doc.getString("username"), doc.getString("passwordHash"),
            Role.valueOf(doc.getString("role")), Boolean.TRUE.equals(doc.getBoolean("active")));
    }
}
