package com.banking.notifications;

import org.bson.Document;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** Creates durable notifications. Writers must insert these in the SAME MongoDB transaction as the business event. */
public final class NotificationOutbox {
    private NotificationOutbox() { }

    public static Document accountOpened(String email, String accountNumber, Date occurredAt) {
        return base("ACCOUNT_CREATED", email, occurredAt)
            .append("accountNumber", Objects.requireNonNull(accountNumber));
    }

    public static Document financial(String event, String email, String accountNumber,
                                     long amountMinor, String reference, Date occurredAt) {
        if (!java.util.Set.of("CREDIT", "DEBIT", "TRANSFER_SENT", "TRANSFER_RECEIVED").contains(event))
            throw new IllegalArgumentException("Unsupported financial email event.");
        if (amountMinor <= 0) throw new IllegalArgumentException("Amount must be positive.");
        return base(event, email, occurredAt)
            .append("accountNumber", Objects.requireNonNull(accountNumber))
            .append("amountMinor", amountMinor)
            .append("reference", Objects.requireNonNull(reference));
    }

    private static Document base(String event, String email, Date occurredAt) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Missing customer email.");
        Date when = Objects.requireNonNull(occurredAt);
        return new Document("notificationId", UUID.randomUUID().toString())
            .append("eventType", event).append("recipientEmail", email)
            .append("status", "PENDING").append("attemptCount", 0)
            .append("createdAt", when).append("updatedAt", new Date());
    }
}
