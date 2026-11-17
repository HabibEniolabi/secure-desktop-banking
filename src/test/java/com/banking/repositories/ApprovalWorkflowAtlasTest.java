package com.banking.repositories;

import com.banking.config.AppConfig;
import com.banking.config.MongoContext;
import com.banking.models.ApprovalTicket;
import com.banking.models.AuthenticatedEmployee;
import com.banking.models.BankingRequest;
import com.banking.models.PostingResult;
import com.banking.models.Role;
import com.banking.services.ApprovalService;
import com.banking.services.EmployeeAccess;
import com.banking.services.PostingPolicy;
import com.banking.services.TransactionService;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses only a disposable sandbox replica-set database. Skipped without TEST_MONGODB_URI.
 * Example: TEST_MONGODB_DATABASE_PREFIX=banking_pr4_it mvn -Dtest=ApprovalWorkflowAtlasTest test
 */
class ApprovalWorkflowAtlasTest {
    @Test void pendingApproveRejectAndInsufficientFunds() {
        String uri = System.getenv("TEST_MONGODB_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank(), "No test MongoDB replica-set URI configured");
        String prefix = System.getenv().getOrDefault("TEST_MONGODB_DATABASE_PREFIX", "banking_pr4_it");
        Assumptions.assumeTrue(prefix.matches("banking_pr4_it[a-zA-Z0-9_]*"), "Isolated test prefix required");
        String name = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (MongoContext context = new MongoContext(new AppConfig(uri, name))) {
            MongoDatabase db = context.database();
            try {
                context.ensureIndexes();
                Date now = new Date();
                db.getCollection("employees").insertMany(java.util.List.of(
                    employee("TELLER", Role.BANK_TELLER), employee("SUPERVISOR", Role.SUPERVISOR)));
                db.getCollection("accounts").insertMany(java.util.List.of(
                    account("1234567890", 10_000_000L, now), account("0987654321", 0L, now)));

                TransactionRepository repo = new TransactionRepository(context.client(), db);
                EmployeeAccess access = new EmployeeAccess(new EmployeeRepository(db));
                TransactionService posting = new TransactionService(repo, access, new PostingPolicy(1_000_000L));
                ApprovalService approvals = new ApprovalService(repo, access);
                AuthenticatedEmployee teller = actor("TELLER", Role.BANK_TELLER);
                AuthenticatedEmployee supervisor = actor("SUPERVISOR", Role.SUPERVISOR);

                BankingRequest request = BankingRequest.transfer(UUID.randomUUID().toString(),
                    "1234567890", "0987654321", "50000.00");
                PostingResult first = posting.submit(teller, request);
                assertTrue(first.pendingApproval());
                assertNull(first.receipt());
                assertEquals(10_000_000L, balance(db, "1234567890"), "Pending request never moves money");
                assertEquals(first.approvalId(), posting.submit(teller, request).approvalId(),
                    "Identical request must be idempotent");
                assertEquals(1, approvals.pending(supervisor, 0, 25).size());
                assertThrows(SecurityException.class,
                    () -> approvals.decide(teller, first.approvalId(), true));
                assertThrows(SecurityException.class,
                    () -> repo.decideApproval("TELLER", first.approvalId(), true));

                ApprovalTicket approved = approvals.decide(supervisor, first.approvalId(), true);
                assertEquals("APPROVED", approved.status());
                assertNotNull(approved.transactionReference());
                assertEquals(5_000_000L, balance(db, "1234567890"));
                assertEquals(5_000_000L, balance(db, "0987654321"));
                assertThrows(IllegalArgumentException.class,
                    () -> approvals.decide(supervisor, first.approvalId(), true),
                    "An approval must never execute twice");
                assertEquals(1L, db.getCollection("transactions").countDocuments(eq("requestId", request.requestId())));

                PostingResult rejected = posting.submit(teller, BankingRequest.credit(
                    UUID.randomUUID().toString(), "1234567890", "20000.00"));
                ApprovalTicket rejectedTicket = approvals.decide(supervisor, rejected.approvalId(), false);
                assertEquals("REJECTED", rejectedTicket.status());
                assertNull(rejectedTicket.transactionReference());
                assertEquals(5_000_000L, balance(db, "1234567890"), "Rejecting cannot credit money");

                PostingResult overdraw = posting.submit(teller, BankingRequest.debit(
                    UUID.randomUUID().toString(), "1234567890", "60000.00"));
                assertThrows(IllegalArgumentException.class,
                    () -> approvals.decide(supervisor, overdraw.approvalId(), true));
                assertEquals(5_000_000L, balance(db, "1234567890"), "Failed approval must roll back");
                assertTrue(approvals.pending(supervisor, 0, 25).stream()
                    .anyMatch(item -> item.approvalId().equals(overdraw.approvalId())),
                    "Failed execution keeps request pending for review");
            } finally {
                db.drop();
            }
        }
    }

    private static Document employee(String id, Role role) {
        return new Document("employeeId", id).append("fullName", id)
            .append("email", id.toLowerCase() + "@example.invalid").append("username", id.toLowerCase())
            .append("passwordHash", "not-used-test-only")
            .append("role", role.name()).append("active", true).append("createdAt", new Date());
    }
    private static Document account(String id, long balance, Date now) {
        return new Document("accountNumber", id).append("customerId", "TEST")
            .append("balanceMinor", balance).append("status", "ACTIVE")
            .append("createdAt", now).append("updatedAt", now);
    }
    private static AuthenticatedEmployee actor(String id, Role role) {
        return new AuthenticatedEmployee(id, id, id.toLowerCase() + "@example.invalid", role);
    }
    private static long balance(MongoDatabase db, String number) {
        return db.getCollection("accounts").find(eq("accountNumber", number)).first().getLong("balanceMinor");
    }
}
