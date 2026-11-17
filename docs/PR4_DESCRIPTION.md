# PR #4 — Financial transactions, concurrency safety and ledger

## Scope (FR-05 through FR-08, NFR-02 and NFR-09)

- Credit, debit and internal transfer via `TransactionService` and `TransactionRepository`.
- Exact money handling: `Money.toMinor` stores amounts in integer kobo (not `double`).
- MongoDB `ClientSession.withTransaction` with `SNAPSHOT` read concern and `MAJORITY` write concern.
- Each account update uses a conditional `$inc` against an ACTIVE account; debits require sufficient balance, credits reject long overflow.
- Transfer commits **both** balance updates, transaction record, two ledger entries and audit event, or commits **none**.
- Unique `requestId` index (already in `MongoContext`) provides idempotency. A repeated requestId/payload returns the original receipt; a different payload or employee is rejected.
- On an unknown write outcome, attempt a lookup by requestId and preserve the original ID on errors. Never initiate another request with a new ID to "retry".
- Paginated read-only transaction history, including transfers; searchable by account number, type and dates through the service API. UI currently has account/type filters.
- Posting and history run on background JavaFX `Task` threads.

## Important limitation: supervisor approvals

`BANKING_TELLER_LIMIT_NGN` defaults to `1000000.00` (₦1 million). Amounts exceeding this limit are **rejected without moving money** until the approval workflow is implemented. No one can override this by hiding buttons. The amount is configurable via the environment and must be consistent across all employees. The supervisor workflow itself is not included in this PR.

## Important limitation: email

SMTP notifications are **not yet sent** by this PR. PR #7 must send only **after successful database commit**; SMTP errors must never roll back a completed transfer.

## Build and run

```bash
mvn clean verify
mvn javafx:run
```

Use a MongoDB Atlas cluster that supports multi-document transactions (replica set/sharded deployment). Do not use standalone MongoDB for this feature. `MongoContext.ensureIndexes()` must succeed.

## Manual acceptance checks

1. Create two ACTIVE accounts with hypothetical funds. Credit account A by ₦100,000; confirm a `transactions` row, `ledger_entries` row and exact account balance.
2. Attempt two simultaneous ₦80,000 debits from an account holding ₦100,000. Only one must commit. Final balance must be ₦20,000.
3. Attempt a transfer to a FROZEN account. Confirm unchanged balances and **no** new transaction/ledger entries.
4. Transfer ₦5,000 from ACTIVE account A to ACTIVE account B. Confirm both balances update together, one transfer record, two ledger entries and a traceable employee ID.
5. Replay same requestId with identical details: no second balance update, same receipt reference. Replay same ID with different amount: rejected.
6. Attempt an operation as CUSTOMER_CARE_OFFICER: rejected in service logic; account balance unchanged.
7. Close the application during a transaction; on restart use the SAME requestId to reconcile before considering a new operation.
8. Confirm `history()` pagination and the JavaFX table show completed transactions (no edit/delete controls).
9. Confirm amounts larger than the configured approval limit are blocked without balance changes.

## Optional concurrency integration test

The optional `TransactionRepositoryAtlasIT` creates and drops a dedicated, randomly named test database. It will NOT run without `TEST_MONGODB_URI`; to run it explicitly:

```bash
TEST_MONGODB_URI='your-test-atlas-uri' mvn -Dtest=TransactionRepositoryAtlasIT test
```

Only use **test credentials** scoped to a dedicated cluster. Never use a production database URI. Its test database name begins with `banking_pr4_it_`. The default test name `*IT` is intentionally excluded from routine Surefire `mvn test` discovery.

## Security caveat from the brief

This is an educational desktop simulation. Direct Atlas connectivity cannot keep a privileged database connection string secret from a user who controls the client machine. Use restricted test credentials and fictional accounts only.
