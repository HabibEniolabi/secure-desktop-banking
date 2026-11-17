package com.banking.repositories;

import com.banking.models.*;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** Ticket changes, history, audit, and notification outbox are committed together. */
public final class TicketRepository {
    private static final TransactionOptions TX = TransactionOptions.builder()
        .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY)
        .readPreference(ReadPreference.primary()).build();
    private final MongoClient client;
    private final MongoCollection<Document> tickets;
    private final MongoCollection<Document> messages;
    private final MongoCollection<Document> notifications;
    private final MongoCollection<Document> audit;

    public TicketRepository(MongoClient client, MongoDatabase database) {
        this.client = Objects.requireNonNull(client);
        tickets = database.getCollection("support_tickets");
        messages = database.getCollection("ticket_messages");
        notifications = database.getCollection("email_notifications");
        audit = database.getCollection("audit_logs");
    }

    public SupportTicket create(String ticketNumber, String customerId, String accountNumber,
                                String transactionReference, TicketCategory category,
                                String description, String actorId, String recipientEmail) {
        Date now = new Date();
        Document ticket = new Document("ticketNumber", ticketNumber)
            .append("customerId", customerId).append("accountNumber", accountNumber)
            .append("transactionReference", transactionReference).append("category", category.name())
            .append("status", TicketStatus.OPEN.name()).append("description", description)
            .append("assignedOfficerId", null).append("createdBy", actorId)
            .append("version", 0L)
            .append("createdAt", now).append("updatedAt", now);
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                tickets.insertOne(session, ticket);
                audit.insertOne(session, auditRecord("TICKET_OPENED", actorId, ticketNumber));
                notifications.insertOne(session, pendingNotice("TICKET_CREATED", recipientEmail,
                    ticketNumber, TicketStatus.OPEN));
                return mapTicket(ticket);
            }, TX);
        }
    }

    public Optional<SupportTicket> findByNumber(String number) {
        return Optional.ofNullable(tickets.find(eq("ticketNumber", number)).first()).map(TicketRepository::mapTicket);
    }

    public List<SupportTicket> search(String query, TicketStatus status, int page, int pageSize) {
        String q = query == null ? "" : query.trim();
        Bson match = q.isBlank() ? new Document() : or(eq("ticketNumber", q), eq("customerId", q),
            eq("accountNumber", q), eq("transactionReference", q));
        if (status != null) match = and(match, eq("status", status.name()));
        List<SupportTicket> items = new ArrayList<>();
        tickets.find(match).sort(Sorts.descending("createdAt", "ticketNumber"))
            .skip(Math.multiplyExact(page, pageSize)).limit(pageSize)
            .forEach(doc -> items.add(mapTicket(doc)));
        return items;
    }

    public List<TicketMessage> messages(String ticketNumber, int page, int size) {
        List<TicketMessage> items = new ArrayList<>();
        messages.find(eq("ticketNumber", ticketNumber))
            .sort(Sorts.ascending("createdAt", "messageId"))
            .skip(Math.multiplyExact(page, size)).limit(size).forEach(d -> items.add(mapMessage(d)));
        return items;
    }

    /** Append response, and optionally change status, with a compare-and-set guard. */
    public SupportTicket update(String ticketNumber, TicketStatus expectedStatus,
                                TicketStatus newStatus, String newAssignee,
                                String actorId, String body, String recipientEmail) {
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> {
                Document previous = tickets.find(session, eq("ticketNumber", ticketNumber)).first();
                if (previous == null) throw new IllegalArgumentException("Ticket not found.");
                if (!Objects.equals(previous.getString("status"), expectedStatus.name()))
                    throw new IllegalStateException("Ticket was changed by another employee. Refresh and retry.");
                Document changed = tickets.findOneAndUpdate(session,
                    and(eq("ticketNumber", ticketNumber), eq("status", expectedStatus.name()),
                        eq("version", previous.getLong("version"))),
                    combine(set("status", newStatus.name()), set("assignedOfficerId", newAssignee),
                        set("updatedAt", new Date()), inc("version", 1L)),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                if (changed == null)
                    throw new IllegalStateException("Ticket was changed by another employee. Refresh and retry.");
                if (body != null && !body.isBlank()) {
                    messages.insertOne(session, new Document("messageId", UUID.randomUUID().toString())
                        .append("ticketNumber", ticketNumber).append("employeeId", actorId)
                        .append("body", body).append("createdAt", new Date()));
                }
                String event = expectedStatus != newStatus ? "TICKET_STATUS_CHANGED" :
                    (!Objects.equals(newAssignee, previous.getString("assignedOfficerId")) ?
                        "TICKET_ASSIGNED" : "TICKET_MESSAGE_ADDED");
                audit.insertOne(session, auditRecord(event, actorId, ticketNumber));
                if (expectedStatus != newStatus) {
                    notifications.insertOne(session, pendingNotice(event, recipientEmail,
                        ticketNumber, newStatus));
                }
                return mapTicket(changed);
            }, TX);
        }
    }

    private static Document auditRecord(String event, String actorId, String ticketNumber) {
        return new Document("event", event).append("employeeId", actorId)
            .append("ticketNumber", ticketNumber).append("createdAt", new Date());
    }

    private static Document pendingNotice(String event, String email, String ticketNumber, TicketStatus status) {
        Date now = new Date();
        return new Document("notificationId", UUID.randomUUID().toString())
            .append("eventType", event).append("recipientEmail", email)
            .append("ticketNumber", ticketNumber).append("ticketStatus", status.name())
            .append("status", "PENDING").append("attemptCount", 0)
            .append("createdAt", now).append("updatedAt", now);
    }

    private static SupportTicket mapTicket(Document d) {
        return new SupportTicket(d.getString("ticketNumber"), d.getString("customerId"),
            d.getString("accountNumber"), d.getString("transactionReference"),
            TicketCategory.valueOf(d.getString("category")), TicketStatus.valueOf(d.getString("status")),
            d.getString("description"), d.getString("assignedOfficerId"), d.getString("createdBy"),
            d.getDate("createdAt").toInstant(), d.getDate("updatedAt").toInstant());
    }

    private static TicketMessage mapMessage(Document d) {
        return new TicketMessage(d.getString("messageId"), d.getString("ticketNumber"),
            d.getString("employeeId"), d.getString("body"), d.getDate("createdAt").toInstant());
    }
}
