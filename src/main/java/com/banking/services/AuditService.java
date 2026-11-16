package com.banking.services;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.Date;

public final class AuditService {
    private final MongoDatabase database;
    public AuditService(MongoDatabase database) { this.database = database; }

    public void loginAttempt(String employeeId, boolean success) {
        database.getCollection("audit_logs").insertOne(new Document("event", success ? "LOGIN_SUCCESS" : "LOGIN_FAILURE")
            .append("employeeId", employeeId == null ? "UNKNOWN" : employeeId)
            .append("createdAt", new Date()));
    }
    public void employeeChange(String actorId, String event, String targetId) {
        database.getCollection("audit_logs").insertOne(new Document("event", event)
            .append("employeeId", actorId).append("targetEmployeeId", targetId)
            .append("createdAt", new Date()));
    }
}
