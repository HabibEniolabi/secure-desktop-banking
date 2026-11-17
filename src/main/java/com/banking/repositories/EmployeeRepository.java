package com.banking.repositories;

import com.banking.models.Employee;
import com.banking.models.Role;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;
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

    public Optional<Employee> findById(String id) {
        return Optional.ofNullable(employees.find(eq("employeeId", id)).first()).map(this::map);
    }

    public List<Employee> search(String query, int page, int pageSize) {
        var filter = query.isBlank() ? new Document() : or(
            regex("fullName", Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)),
            regex("email", Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)),
            regex("username", Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)),
            eq("employeeId", query));
        List<Employee> result = new ArrayList<>();
        employees.find(filter).sort(Sorts.ascending("fullName", "employeeId"))
            .skip(Math.multiplyExact(page, pageSize)).limit(pageSize).into(new ArrayList<>())
            .forEach(d -> result.add(map(d)));
        return result;
    }

    public long countActiveAdministrators() {
        return employees.countDocuments(com.mongodb.client.model.Filters.and(
            eq("role", Role.ADMINISTRATOR.name()), eq("active", true)));
    }

    public Employee create(String fullName, String email, String username, String passwordHash, Role role) {
        String id = UUID.randomUUID().toString();
        Date now = new Date();
        Document doc = new Document("employeeId", id).append("fullName", fullName)
            .append("email", email).append("username", username)
            .append("passwordHash", passwordHash).append("role", role.name())
            .append("active", true).append("createdAt", now).append("updatedAt", now);
        try { employees.insertOne(doc); }
        catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == com.mongodb.ErrorCategory.DUPLICATE_KEY)
                throw new IllegalArgumentException("Employee email or username already exists.", ex);
            throw ex;
        }
        return map(doc);
    }

    public Optional<Employee> update(String id, String fullName, String email, Role role, boolean active) {
        try {
            var updated = employees.findOneAndUpdate(eq("employeeId", id),
                combine(set("fullName", fullName), set("email", email), set("role", role.name()),
                    set("active", active), set("updatedAt", new Date())),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            return Optional.ofNullable(updated).map(this::map);
        } catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == com.mongodb.ErrorCategory.DUPLICATE_KEY)
                throw new IllegalArgumentException("Employee email already exists.", ex);
            throw ex;
        }
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
