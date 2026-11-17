# PR #3 — Customer registration and bank accounts

## Requirements mapped

- **FR-03:** Create, search (name, email, phone, ID, account number), view and update fictitious customers. Validate required fields, email, telephone, address, and past dates of birth in Java service code. Persist creation/update timestamps and enforce unique customer IDs and emails through MongoDB indexes.
- **FR-04:** Create customer-linked accounts with ten-digit numbers, zero opening balance, initial `ACTIVE` status, timestamps, and a MongoDB unique index. Search by exact account number, list the selected customer's accounts, and inspect the account holder. Supervisor can freeze/unfreeze and close zero-balance accounts; closed accounts cannot reopen.
- **RBAC:** Teller may register/update customers and create accounts. Customer-care may view customers/accounts. Supervisor may view and restrict accounts. All server-side actions re-fetch the employee's active role before permission checks.
- **NFRs:** MongoDB indexes, bounded pagination, pooled client inherited from PR #1; background tasks keep the UI responsive. No secrets checked into source; balances use long integer kobo.

## Known limitations and next PR

- No deposits, withdrawals, transfers, transaction history, ledger, approvals, SMTP notifications, or reports in this PR. These belong to later PRs. The `balanceMinor` field is initialized to zero and is not changed by this module.
- Atlas connectivity and the JavaFX GUI need a configured local machine and a test database. The project is **not safe for real banking**: the direct desktop-to-Atlas architecture is an educational assignment constraint.
- Before testing, ensure you have merged PR #2, run `mvn clean verify`, and configure a fictitious MongoDB Atlas database.

## Manual smoke checks

1. Log in as a teller, register a customer; verify normalized email and phone, DOB validation and duplicate email rejection.
2. Search by name, email and customer ID; edit customer details; open two accounts for one customer.
3. Search by ten-digit account number and verify account owner, zero balance and `ACTIVE` status.
4. Log in as customer-care officer: search works; create/update buttons are disabled and direct service calls are rejected.
5. Log in as supervisor: freeze, unfreeze and close a zero-balance account. Verify a closed account cannot be reopened.
6. Confirm audit entries for customer changes, account openings and account status changes.
