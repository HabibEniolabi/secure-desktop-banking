package com.banking.notifications;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs off the JavaFX thread; database operations and email delivery never block navigation. */
public final class NotificationWorker implements AutoCloseable {
    private final NotificationRepository repository;
    private final EmailSender sender;
    private ScheduledExecutorService executor;

    public NotificationWorker(MongoDatabase database, EmailSender sender) {
        this.repository = new NotificationRepository(Objects.requireNonNull(database));
        this.sender = Objects.requireNonNull(sender);
    }

    /** One batch per invocation, maximum 20 notifications to avoid monopolizing the worker. */
    public int processBatch() {
        int processed = 0;
        while (processed < 20) {
            Document claim = repository.claimNext();
            if (claim == null) break;
            processed++;
            try {
                Long amount = claim.getLong("amountMinor");
                EmailTemplate.Message email = EmailTemplate.render(
                    claim.getString("eventType"), claim.getString("accountNumber"), amount,
                    claim.getString("reference"), claim.getString("ticketNumber"),
                    claim.getString("ticketStatus"), claim.getDate("createdAt"));
                sender.send(claim.getString("recipientEmail"), email);
                repository.markSent(claim);
            } catch (Exception error) {
                repository.markFailed(claim, error);
            }
        }
        return processed;
    }

    public synchronized void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "banking-email-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { processBatch(); }
            catch (Exception error) {
                // Retain pending outbox records; never disclose credentials, addresses or connection URLs.
                System.err.println("Email notification check failed; will retry.");
            }
        }, 2, 15, TimeUnit.SECONDS);
    }

    @Override public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
