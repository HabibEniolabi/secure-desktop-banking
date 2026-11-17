# PR #7 — Gmail SMTP and durable email notifications

## What this PR implements

- Gmail SMTP (`smtp.gmail.com:587`, STARTTLS **required**) using Jakarta/Angus Mail already included in `pom.xml`.
- `SMTP_USERNAME` and Gmail `SMTP_APP_PASSWORD` supplied through environment variables; no secrets in source.
- **Transactional outbox**: account creation, credit, debit and transfers commit their notification records together with the account/ledger updates in MongoDB. Customer Care's existing ticket notifications use that same outbox.
- A single-threaded, daemon email worker runs independently of the JavaFX Application Thread. It starts after employee login when SMTP is configured, and stops at logout/application exit.
- Atomic claim/lease (`PROCESSING`), attempt counts, `SENT` and `FAILED` statuses, backoff for failures and permanent failure after five unsuccessful attempts. Leases recover when a desktop exits mid-send.
- Plain-text, UTF-8 messages with transaction type, amount, reference, date (UTC), and masked account suffixes; complaint notifications include ticket number/status.

## Critical consistency rule

No SMTP operation takes place inside a MongoDB multi-document transaction. Sending can occur only after the notification record is visible in the committed `email_notifications` collection. Email failure never reverses already committed banking operations. When SMTP is not configured, notices remain pending until an operator configures SMTP and signs in again.

## Local configuration

1. Use **fictional** customers and simulated funds, with test inboxes you control.
2. Enable Google account two-step verification and create a Gmail App Password where supported. Do **not** use the normal account password; never share the App Password in issues or chat.
3. In the project's local `.env` (never commit it), set:

   ```dotenv
   SMTP_USERNAME="your-test-sender@gmail.com"
   SMTP_APP_PASSWORD="your-google-app-password"
   ```

4. Load environment variables and start the app:

   ```bash
   set -a; source .env; set +a
   mvn clean verify
   mvn javafx:run
   ```

5. Sign in; creating a new account, credit/debit/transfer, registering a ticket or changing ticket status queues a notice. While the app is open and signed in, the worker sends queued messages.
6. Inspect `email_notifications` in Atlas. Useful fields: `eventType`, `status`, `attemptCount`, `sentAt`, `lastAttemptAt`, `lastErrorType`, `nextAttemptAt` (avoid displaying recipient emails to spectators).

## Test checklist

- A new account results in one `ACCOUNT_CREATED` outbox record and one delivered email.
- Credit and debit result in one notification each; transfers result in one debit and one credit notice for the two affected accounts.
- A failed/rolled-back transfer has **no** queued notification, no partial balance update.
- Ticket registration and status changes result in outbox notifications.
- Disable SMTP credentials: completed banking operations remain valid, pending outbox records do not disappear.
- Configure incorrect App Password: notices become `FAILED`, with increasing attempts and backoff; financial records remain unchanged.
- Start two desktop sessions on the same Atlas database: the claim/lease ensures only one worker claims an entry at a time.
- Email templates never expose the full customer account number and explain the simulation.
- Run `mvn clean verify` (unit tests) and then perform real Atlas+SMTP integration tests using test credentials.

## Delivery semantics and limitations

SMTP cannot participate in a MongoDB transaction. There is a narrow window in which Gmail may accept an email and the status update back to MongoDB can fail; subsequent retries may send a **duplicate**. The implementation provides *at-least-once* delivery (subject to the configured retry cap), not guaranteed exactly-once SMTP delivery. Do not use real banking data or funds. Direct database credentials in a desktop app are suitable only for this controlled classroom simulation.
