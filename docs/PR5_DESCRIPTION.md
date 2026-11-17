# PR #5 — Supervisor approvals

## Added
- High-value teller requests are persisted as PENDING without changing account balances.
- Same request UUID returns the same approval request on an identical resubmission.
- Unique database index on approval request ID prevents duplicate pending requests.
- Active supervisors alone may approve/reject (role rechecked in application service and inside the approval transaction).
- Supervisor cannot decide their own request.
- An approval decision and, for approval, its credit/debit/transfer and ledger/audit records commit atomically.
- Approved execution rechecks initiator authorization, balances and ACTIVE account status; failures leave the request pending.
- Approval/rejection activity is recorded; rejection never changes funds.
- JavaFX supervisor dashboard includes pending requests with approve/reject actions; teller receives a distinct pending receipt.

## Limitations and tests
Run `mvn clean verify` and `mvn javafx:run` with JDK 21. Verify the optional live-Atlas approval test with a dedicated sandbox database. There are no SMTP notifications yet.

The app connects to MongoDB directly as an **educational simulation only**. Desktop users with DB credentials can circumvent application RBAC; use limited Atlas credentials and simulated accounts.
