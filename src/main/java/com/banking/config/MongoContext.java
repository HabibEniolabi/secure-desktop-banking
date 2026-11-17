package com.banking.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import java.util.concurrent.TimeUnit;

/** One shared thread-safe MongoDB client with the driver's built-in connection pool. */
public final class MongoContext implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoContext(AppConfig config) {
        var settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(config.mongoUri()))
            .applyToSocketSettings(b -> b.connectTimeout(10, TimeUnit.SECONDS))
            .applyToClusterSettings(b -> b.serverSelectionTimeout(10, TimeUnit.SECONDS))
            .build();
        this.client = MongoClients.create(settings);
        this.database = client.getDatabase(config.mongoDatabase());
    }

    public MongoDatabase database() { return database; }
    public MongoClient client() { return client; }

    public void ensureIndexes() {
        unique("employees", "email");
        unique("employees", "username");
        unique("customers", "customerId");
        unique("customers", "email");
        unique("accounts", "accountNumber");
        database.getCollection("accounts").createIndex(Indexes.ascending("customerId", "accountNumber"));
        database.getCollection("customers").createIndex(Indexes.ascending("fullName", "customerId"));
        unique("transactions", "reference");
        unique("transactions", "requestId");
        unique("support_tickets", "ticketNumber");
        unique("approvals", "approvalId");
        unique("email_notifications", "notificationId");
        database.getCollection("transactions").createIndex(Indexes.ascending("accountNumber", "createdAt"));
        database.getCollection("transactions").createIndex(Indexes.ascending("createdAt"));
        database.getCollection("transactions").createIndex(Indexes.ascending("sourceAccountNumber", "createdAt"));
        database.getCollection("transactions").createIndex(Indexes.ascending("destinationAccountNumber", "createdAt"));
        database.getCollection("ledger_entries").createIndex(Indexes.ascending("reference", "createdAt"));
        database.getCollection("support_tickets").createIndex(Indexes.ascending("customerId", "status"));
        database.getCollection("ticket_messages").createIndex(Indexes.ascending("ticketNumber", "createdAt"));
        database.getCollection("audit_logs").createIndex(Indexes.descending("createdAt"));
    }

    private void unique(String collection, String field) {
        database.getCollection(collection).createIndex(Indexes.ascending(field), new IndexOptions().unique(true));
    }

    @Override public void close() { client.close(); }
}
