# Architecture and design notes

```text
JavaFX UI ──> UI controllers ──> services / validators / security
                                         │
                              repositories + MongoContext
                                         │
                                  MongoDB Atlas (TLS)

A notification outbox will later enqueue in a DB transaction,
then a background worker will deliver using Gmail SMTP.
```

This follows the assignment constraint of **no backend server**. The direct Atlas credentials are inherently extractable from desktop clients, so use only a controlled educational environment, restricted database user, and simulated data.

## Core collections

`employees`, `customers`, `accounts`, `transactions`, `ledger_entries`, `support_tickets`, `ticket_messages`, `approvals`, `email_notifications`, `audit_logs`. Indexes are set in `MongoContext.ensureIndexes()`.

## Financial consistency design for PR #4

- Use **long minor units** for money (`₦1.00 = 100 kobo`). Validate positive and bounded amounts.
- Assign a stable client-generated idempotency reference **before** starting a request and reuse it on any retry.
- Unique index on `transactions.reference` and `transactions.requestId`.
- Debit using `findOneAndUpdate(session, and(eq(accountNumber),eq(status,"ACTIVE"),gte("balanceMinor", amount)), inc("balanceMinor", -amount))`.
- Wrap balance changes, transaction record, and ledger entries in a MongoDB **multi-document transaction** and commit atomically. Recheck active status and balances inside the session.
- If the transaction result is unknown after connection loss, query the *same original reference* before any retry. Do not generate a new reference.
- For transfers, debit source and credit destination inside the **same** Mongo transaction; never sequential independent writes.
- The notification outbox record must be created in the committed transaction; asynchronous sending is performed afterward and email failure must not undo a committed financial operation.

## Security research notes

BCrypt salts and stretches employee passwords. Authentication verifies hashes and checks `active == true`. Role permissions are enforced in application services, not inferred from display state. Inputs are validated before insert/update. MongoDB TLS URI and Gmail App Password belong only in process environment; `.env` is ignored by Git.

## Scalability notes

Reuse one MongoDB client/pool, apply indexed exact identifiers, and use `limit/skip` initially (cursor-based pagination can be added as collections grow). Avoid full collection scans for transaction-history screens. Expose aggregate-based report summaries rather than transferring all transactions to Java.
