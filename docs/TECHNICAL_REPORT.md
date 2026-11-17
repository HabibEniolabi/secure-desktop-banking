# Technical Report — Secure Desktop Banking and Customer Care Management System

**Project type:** Educational desktop banking simulation  
**Language/UI:** Java 21 + JavaFX  
**Database:** MongoDB Atlas (synchronous Java driver)  
**Email:** Gmail SMTP using Angus/Jakarta Mail  
**Build:** Maven  
**Prepared:** 17 November 2026

## 1. Overview and scope

The desktop application supports employee authentication, role-controlled customer and account management, simulated credit/debit/transfer operations, restricted transaction approvals, customer complaints, asynchronous email notifications and operational reports. It directly accesses MongoDB Atlas; no REST API or separate backend is used, as specified in the project brief. All customers and funds are fictional. The program is **not suitable for real financial operations** or public distribution with embedded database credentials.

## 2. Architecture and package separation

`com.banking.Main` owns the JavaFX login/dashboard navigation. The `ui` package contains screens and background `Task` dispatchers. The `services` package performs authorization and business rules; `repositories` encapsulates MongoDB queries, transactions, conditional balance updates and persistence. `validators` and `security` handle input checks, BCrypt and role permissions. `notifications` separates SMTP transport, message templating, durable notification outbox records and delivery retries. `models` contains immutable Java records and enums; `config.MongoContext` creates one reusable MongoDB client and initializes indexes.

Logical flow: **JavaFX screen → service (fresh role check and validation) → repository (MongoDB) → committed record/outbox → asynchronous email worker → Gmail SMTP**.

## 3. Data model and indexes

MongoDB collections include `employees`, `customers`, `accounts`, `transactions`, `ledger_entries`, `support_tickets`, `ticket_messages`, `approvals`, `email_notifications` and `audit_logs`. Unique indexes enforce employee email/username, customer ID/email, account number, transaction reference and request ID, complaint ticket number, approval/request identifiers and notification identifiers. Secondary indexes support customer lookup, account lists, time-sorted transaction and audit records, ticket status, and report queries on transaction status and date. Pagination limits record retrieval; the report screen caps recent transactions and audit rows at 30 each.

Account balances and financial amounts use **integer kobo stored as signed 64-bit values**, avoiding floating-point rounding. Money input is parsed using `BigDecimal`, with exact conversion to minor units.

## 4. Security and privacy

Employee passwords are hashed with BCrypt using per-password salts, rather than stored as plain text or reversible ciphertext. Hashing is one-way; encryption is reversible with a key. BCrypt's adaptive work factor slows password guessing; a fast general-purpose hash such as unsalted SHA-256 is unsuitable for password storage. The bootstrap administrator is explicitly seeded from environment variables, rather than being a default hardcoded login.

The system implements four roles: Administrator, Bank Teller, Customer Care Officer and Supervisor. UI controls are hidden for unauthorized roles **and every protected service operation rechecks the employee's current active status and permissions in MongoDB**. Customer Care cannot post financial transactions. Financial activity, account status changes, logins and complaint changes generate audit entries. Email templates mask account numbers. The `.env` file, MongoDB credentials, Gmail App Password and administrator seed password must never be committed or included in screenshots.

**Security limitation:** The brief requires a direct desktop-to-Atlas connection. Anyone who controls a distributed desktop binary or its runtime environment may be able to extract database credentials. Use a restricted coursework-only Atlas user, narrow network access, simulated data and a nonproduction Gmail account. Account permissions in the application do not replace database-side isolation.

## 5. Correctness, race conditions and ACID transactions

A race condition happens when concurrent operations observe or update shared data in an unsafe order. For debits, the repository uses MongoDB `findOneAndUpdate()` with a conditional `balanceMinor >= requested amount` filter and an atomic `$inc` decrement; concurrent tellers cannot both withdraw funds that only cover one withdrawal. No code reads a balance into Java and then overwrites it without a concurrency guard.

Credit, debit and transfer posting uses a MongoDB **multi-document transaction** with `ClientSession`. It commits account balance updates, transaction record, ledger entries, audit entry and associated notification records together, or rolls them all back. A transfer is atomic: either the source debit and destination credit are both committed or neither is. Transactions run with snapshot reads, majority writes and primary read preference. This applies ACID properties: **Atomicity** (all-or-nothing posting), **Consistency** (balance and ledger invariants), **Isolation** (concurrent transactions do not observe partial writes) and **Durability** (majority-acknowledged commits).

A unique request ID is used for **idempotency**. If the network fails after an uncertain commit result, the UI should keep the same ID; the repository checks for a previously committed request rather than blindly posting a new one. For restricted amounts, the teller creates an approval request without modifying balances. The supervisor cannot approve a self-initiated request, and authorization, account status and funds are rechecked at approval time. A rejected request never moves money.

Customer Care uses a ticket version field as **optimistic locking**: the update succeeds only when the previously read version/status still matches, avoiding silent lost updates.

## 6. Email and reliability

Gmail SMTP is configured through environment variables using authenticated STARTTLS on port 587. SMTP **sends** messages; IMAP and POP3 are protocols for **retrieving** mail. TLS encrypts the transport; Gmail App Passwords provide application-specific authentication on eligible accounts without storing the normal Google account password in source code.

Account, transaction and complaint changes create durable outbox records inside their database transaction. A separate scheduled worker claims and sends records, tracks delivery attempts and retries failures. Email sending is never performed before the associated DB commit or on the JavaFX Application Thread. An SMTP failure does not undo committed money movement. Delivery can be **at least once** across rare network/confirmation failures, so duplicate emails can occur even when the financial operation is idempotent.

## 7. Reporting and performance

The Reports screen is available to Administrators and Supervisors with `VIEW_REPORTS`. It shows current customer, active/frozen account, unresolved ticket and pending approval counts; completed credit/debit/transfer sums for a selected UTC date range; and up to 30 recent transactions. Administrators additionally see up to 30 date-filtered audit events. Filtering uses an inclusive start day and exclusive midnight after the end day, avoiding missing transactions late in the final day. MongoDB `$match` and `$group`/`$sum` aggregate financial totals in the database rather than transferring all records to Java. The report queries run asynchronously to keep JavaFX responsive. Snapshot counts are live current-state values; they are not historical reconstructed balances or one atomic multi-collection snapshot.

The MongoDB client is reused for connection pooling. Indexes, paginated list screens and bounded report queries support larger datasets without loading the entire database.

## 8. Build and environment

Required: JDK 21, Maven 3.9+, an Atlas deployment supporting multi-document transactions, a database user with least-necessary rights and an allowed client IP. Configure `MONGODB_URI`, `MONGODB_DATABASE`, temporary `ADMIN_*` credentials and optional `SMTP_*` variables in an **ignored `.env` file**. To create the initial admin once, run the `com.banking.BootstrapAdmin` class; unset `ADMIN_PASSWORD` before normal operation. Run `mvn clean verify` and `mvn javafx:run` to build and launch. Gmail delivery only starts when valid SMTP environment configuration is present.

## 9. Verification and outstanding evidence

Unit tests address hashing/authorization, input and money validation, posting policy, reporting dates and ticket rules. Optional Atlas integration tests cover transaction concurrency, rollback and approval sequencing; they require real test-database configuration and must not be assumed to have run merely because Maven compiles. End-to-end tests—four role logins, account creation, two simultaneous debit requests against insufficient combined funds, transfer failure rollback, duplicate request replay, supervisor decisions, complaint escalation, email receipt and report totals—are documented in `docs/SUBMISSION_CHECKLIST.md` and **remain to be executed and evidenced**. No claim of full production readiness or completed live integration testing is made.

## 10. Research references

- OWASP, *Password Storage Cheat Sheet* — password hashing and adaptive work factors: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- MongoDB, *Transactions*: https://www.mongodb.com/docs/manual/core/transactions/
- MongoDB, *Atomicity and Transactions*: https://www.mongodb.com/docs/manual/core/write-operations-atomicity/
- MongoDB, *Indexes*: https://www.mongodb.com/docs/manual/indexes/
- MongoDB, *Java Sync Driver*: https://www.mongodb.com/docs/drivers/java/sync/current/
- OpenJFX, *Task*: https://openjfx.io/javadoc/21/javafx.graphics/javafx/concurrent/Task.html
- Google, *Sign in with app passwords*: https://support.google.com/accounts/answer/185833

This report describes the project implementation and design; functional claims about live services must be backed by test results and screenshots before final submission.
