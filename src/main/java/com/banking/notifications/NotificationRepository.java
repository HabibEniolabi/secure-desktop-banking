package com.banking.notifications;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** A lease prevents two running desktops from claiming the same pending email concurrently. */
public final class NotificationRepository {
    private final MongoCollection<Document> notifications;
    private static final int MAX_ATTEMPTS = 5;

    public NotificationRepository(MongoDatabase database) {
        notifications = database.getCollection("email_notifications");
    }

    /** Returns a locked outbox entry; previous unfinished leases can be reclaimed after 90 seconds. */
    public Document claimNext() {
        Date now = new Date();
        Bson eligible = or(eq("status", "PENDING"),
            and(eq("status", "FAILED"), lte("nextAttemptAt", now), lt("attemptCount", MAX_ATTEMPTS)),
            and(eq("status", "PROCESSING"), lt("leaseUntil", now), lt("attemptCount", MAX_ATTEMPTS)));
        return notifications.findOneAndUpdate(eligible,
            combine(set("status", "PROCESSING"), set("claimToken", UUID.randomUUID().toString()),
                set("leaseUntil", new Date(now.getTime() + TimeUnit.SECONDS.toMillis(90))),
                set("lastAttemptAt", now), set("updatedAt", now), inc("attemptCount", 1)),
            new FindOneAndUpdateOptions().sort(Sorts.ascending("createdAt", "notificationId"))
                .returnDocument(ReturnDocument.AFTER));
    }

    public void markSent(Document claimed) {
        notifications.updateOne(claimFilter(claimed), combine(set("status", "SENT"),
            set("sentAt", new Date()), set("updatedAt", new Date()),
            unset("claimToken"), unset("leaseUntil"), unset("nextAttemptAt"), unset("lastErrorType")));
    }

    public void markFailed(Document claimed, Exception failure) {
        int tries = claimed.getInteger("attemptCount", 1);
        boolean exhausted = tries >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(3600L, 30L * (1L << Math.min(tries - 1, 6)));
        Date now = new Date();
        // Only store exception type; raw SMTP text can contain email addresses or server details.
        notifications.updateOne(claimFilter(claimed), combine(
            set("status", exhausted ? "PERMANENT_FAILURE" : "FAILED"),
            set("lastErrorType", failure.getClass().getSimpleName()),
            set("nextAttemptAt", new Date(now.getTime() + TimeUnit.SECONDS.toMillis(delaySeconds))),
            set("updatedAt", now), unset("claimToken"), unset("leaseUntil")));
    }

    private static Bson claimFilter(Document claimed) {
        return and(eq("notificationId", claimed.getString("notificationId")),
            eq("status", "PROCESSING"), eq("claimToken", claimed.getString("claimToken")));
    }
}
