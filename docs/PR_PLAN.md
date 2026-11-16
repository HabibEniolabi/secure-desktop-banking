# Pull-request implementation plan

Deadline: **17 November 2026**. Prioritize working integration and financial safety over styling.

| PR | Branch | Deliverables | Acceptance |
|---|---|---|---|
| 1 | `chore/project-foundation` | Maven, JavaFX, Atlas config, indexes, BCrypt login, RBAC, CI | Admin can be seeded and can log in; unauthorized access is rejected |
| 2 | `feat/employee-management` | Admin-managed employee create/update/disable, search; authorization; uniqueness | Non-admin cannot manage employees |
| 3 | `feat/customer-accounts` | Customer forms/search, account creation/status, multiple accounts | Duplicates rejected by indexes; frozen account restrictions |
| 4 | `feat/safe-transactions` | Credit/debit/transfer; ledger; idempotency; MongoDB transactions | Two simultaneous 80k debits from 100k => one success; no partial transfer; duplicate request charged once |
| 5 | `feat/history-approvals` | Paginated history, filters, supervisor-only approvals and revalidation | No teller self-approval; rejected requests do not change balances |
| 6 | `feat/customer-care` | Complaint lifecycle, messages, escalation | Only authorized staff can change tickets; care cannot debit |
| 7 | `feat/email-notifications` | Gmail SMTP, async outbox, attempts and failure tracking | Email after commit only; SMTP failure does not roll back balances |
| 8 | `feat/reports-hardening` | Dashboards, aggregates, audit trail, tests, documentation, demo dataset | Assignment checklist verified by demonstration |

## Order and risk

1. Get Atlas access and seed the administrator.
2. Build customer/account flows, so transactions have valid entities.
3. Implement transactions **before** improving look and feel.
4. Add a deterministic concurrency test on a transaction-capable MongoDB test deployment.
5. Verify each feature while implementing it, not only on submission day.

## Project evidence to capture

- Successful/failed login and role restrictions.
- Customer creation, uniqueness and account statuses.
- Credit/debit/transfer receipts and immutable ledger/transaction history.
- Parallel debit test; rollback after injected transfer failure.
- Retry of the same idempotency key; single transaction recorded.
- Ticket creation/escalation and SMTP success/failure evidence.
- MongoDB indexes and a short technical report explaining BCrypt, ACID, `$inc`, session transactions, indexes, pagination, and SMTP.
