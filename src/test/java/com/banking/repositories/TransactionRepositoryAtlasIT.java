package com.banking.repositories;

import com.banking.config.AppConfig;
import com.banking.config.MongoContext;
import com.banking.models.BankingRequest;
import com.banking.models.TransactionReceipt;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional live replica-set integration test. NEVER points at the application database.
 * Run with TEST_MONGODB_URI and an isolated TEST_MONGODB_DATABASE_PREFIX=banking_pr4_it.
 * No Atlas URI => skipped. A test DB is created and dropped on completion.
 */
class TransactionRepositoryAtlasIT {
    @Test void concurrentDebitsTransferRollbackAndIdempotentRetry() throws Exception {
        String uri = System.getenv("TEST_MONGODB_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank(), "No test Atlas URI configured");
        String prefix = System.getenv().getOrDefault("TEST_MONGODB_DATABASE_PREFIX", "banking_pr4_it");
        Assumptions.assumeTrue(prefix.matches("banking_pr4_it[a-zA-Z0-9_]*"),
            "Dedicated integration test database prefix required");
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (MongoContext ctx = new MongoContext(new AppConfig(uri, dbName))) {
            MongoDatabase db = ctx.database();
            try {
                ctx.ensureIndexes();
                Date when = new Date();
                db.getCollection("accounts").insertOne(account("1234567890", 10_000_000L, "ACTIVE", when));
                db.getCollection("accounts").insertOne(account("0987654321", 0L, "FROZEN", when));
                TransactionRepository repository = new TransactionRepository(ctx.client(), db);

                CountDownLatch go = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    Future<Boolean> a = executor.submit(() -> debitAfterLatch(repository, go));
                    Future<Boolean> b = executor.submit(() -> debitAfterLatch(repository, go));
                    go.countDown();
                    assertTrue(a.get() ^ b.get(), "Exactly one of two ₦80,000 debits should succeed");
                }
                assertEquals(2_000_000L, balance(db, "1234567890"));

                BankingRequest transfer = BankingRequest.transfer(UUID.randomUUID().toString(),
                    "1234567890", "0987654321", "5000");
                assertThrows(IllegalArgumentException.class, () -> repository.post("TEST-TELLER", transfer, 100_000_000L));
                assertEquals(2_000_000L, balance(db, "1234567890"), "Failed transfer must roll back debit");
                db.getCollection("accounts").updateOne(eq("accountNumber", "0987654321"), set("status", "ACTIVE"));
                TransactionReceipt receipt = repository.post("TEST-TELLER", transfer, 100_000_000L);
                TransactionReceipt replay = repository.post("TEST-TELLER", transfer, 100_000_000L);
                assertEquals(receipt.reference(), replay.reference());
                assertEquals(1_500_000L, balance(db, "1234567890"));
                assertEquals(500_000L, balance(db, "0987654321"));
                assertEquals(2L, db.getCollection("ledger_entries").countDocuments(eq("reference", receipt.reference())));
                assertThrows(IllegalArgumentException.class, () -> repository.post("TEST-TELLER",
                    BankingRequest.transfer(transfer.requestId(), "1234567890", "0987654321", "10"), 100_000_000L));
            } finally {
                db.drop();
            }
        }
    }

    private static boolean debitAfterLatch(TransactionRepository repo, CountDownLatch gate) throws Exception {
        gate.await();
        try {
            repo.post("TEST-TELLER", BankingRequest.debit(UUID.randomUUID().toString(), "1234567890", "80000"),
                100_000_000L);
            return true;
        } catch (IllegalArgumentException ex) {
            if (!ex.getMessage().contains("Insufficient funds")) throw ex;
            return false;
        }
    }

    private static Document account(String number, long amount, String status, Date when) {
        return new Document("accountNumber", number).append("customerId", "TEST-CUSTOMER")
            .append("balanceMinor", amount).append("status", status)
            .append("createdAt", when).append("updatedAt", when);
    }

    private static long balance(MongoDatabase db, String number) {
        return db.getCollection("accounts").find(eq("accountNumber", number)).first().getLong("balanceMinor");
    }
}
