package com.banking.repositories;

import com.banking.models.Customer;
import com.banking.validators.CustomerValidator.CustomerData;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public final class CustomerRepository {
    private final MongoCollection<Document> customers;
    private final MongoCollection<Document> accounts;

    public CustomerRepository(MongoDatabase database) {
        customers = database.getCollection("customers");
        accounts = database.getCollection("accounts");
    }

    public Optional<Customer> findById(String customerId) {
        return Optional.ofNullable(customers.find(eq("customerId", customerId)).first()).map(CustomerRepository::map);
    }

    public Customer create(CustomerData data) {
        String id = "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT);
        Date now = new Date();
        Document doc = new Document("customerId", id)
            .append("fullName", data.fullName()).append("email", data.email())
            .append("phone", data.phone()).append("address", data.address())
            .append("dateOfBirth", toDate(data.dateOfBirth()))
            .append("createdAt", now).append("updatedAt", now);
        try { customers.insertOne(doc); }
        catch (MongoWriteException ex) { throw duplicateError(ex); }
        return map(doc);
    }

    public Optional<Customer> update(String customerId, CustomerData data) {
        try {
            var updated = customers.findOneAndUpdate(eq("customerId", customerId),
                combine(set("fullName", data.fullName()), set("email", data.email()),
                    set("phone", data.phone()), set("address", data.address()),
                    set("dateOfBirth", toDate(data.dateOfBirth())), set("updatedAt", new Date())),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            return Optional.ofNullable(updated).map(CustomerRepository::map);
        } catch (MongoWriteException ex) { throw duplicateError(ex); }
    }

    public List<Customer> search(String query, int page, int pageSize) {
        String term = query == null ? "" : query.trim();
        String owner = term.matches("[0-9]{10}") ? accountOwnerId(term) : "";
        var filter = term.isEmpty() ? new Document() : or(
            eq("customerId", term), eq("email", term.toLowerCase(java.util.Locale.ROOT)),
            eq("phone", term), regex("fullName", Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE)),
            eq("customerId", owner));
        List<Customer> output = new ArrayList<>();
        customers.find(filter).sort(Sorts.ascending("fullName", "customerId"))
            .skip(Math.multiplyExact(page, pageSize)).limit(pageSize).forEach(d -> output.add(map(d)));
        return output;
    }

    private String accountOwnerId(String accountNumber) {
        Document match = accounts.find(eq("accountNumber", accountNumber)).projection(new Document("customerId", 1)).first();
        return match == null ? "" : match.getString("customerId");
    }

    private static RuntimeException duplicateError(MongoWriteException ex) {
        if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY)
            return new IllegalArgumentException("A customer with that email or identifier already exists.", ex);
        return ex;
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private static Customer map(Document d) {
        return new Customer(d.getString("customerId"), d.getString("fullName"), d.getString("email"),
            d.getString("phone"), d.getString("address"),
            d.getDate("dateOfBirth").toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
            d.getDate("createdAt").toInstant(), d.getDate("updatedAt").toInstant());
    }
}
