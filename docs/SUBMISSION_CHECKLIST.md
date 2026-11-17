# Submission Checklist and Deferred Test Plan

**Deadline in the supplied brief:** Tuesday, 17 November 2026.  
**Scope:** JavaFX coursework simulation, not a real banking product.

## Deliverables and code preparation

- [x] Java 21/Maven project structure, JavaFX entrypoint and source packages
- [x] BCrypt login, explicit initial administrator bootstrap, active employee status and RBAC code
- [x] Employee management, customer management and account management modules
- [x] Credit, debit, transfer, ledger and history implementation
- [x] Conditional balance update and MongoDB multi-document transaction code
- [x] Restricted transfer/withdrawal approval workflow code
- [x] Customer Care tickets, escalation and message-history implementation
- [x] Gmail SMTP templates, durable outbox, retry worker and status records
- [x] FR-12 operational dashboard, date filters, aggregation and bounded audit/transaction tables
- [x] Short technical report at `docs/TECHNICAL_REPORT.md`
- [ ] **Full Maven build and GitHub Actions must pass on final merged revision**
- [ ] **Live MongoDB Atlas end-to-end verification**
- [ ] **Live Gmail SMTP delivery verification**
- [ ] **Evidence/screenshots and final hand-in**

These checkmarks indicate source-code implementation exists, **not that the feature has been proven operational against live services**.

## Before hand-in

1. Merge PR #7 (Gmail SMTP), then PR #8 (Dashboard & Reports), in that order. Both must target `main`.
2. Confirm Java 21 is used: `mvn -version`.
3. Run `mvn clean verify`, then `mvn javafx:run`. Check GitHub Actions has a green verification run.
4. Verify your ignored `.env`: `git check-ignore .env`. Never upload `.env` or paste connection strings/passwords into screenshots.
5. Rotate any credentials ever exposed in screenshots or chat, including the Atlas database password, Gmail App Password and initial admin password. Keep the repository private or use only a restricted simulation account.
6. Put your repository URL, technical report and required evidence together with the submission. Confirm university-specific naming/format instructions before uploading.

## Deferred functional test matrix (test with fictitious customers/funds)

| ID | Actor | Action | Expected evidence |
|---|---|---|---|
| T01 | Administrator | Log in; add teller, supervisor and care officer | Distinct roles; BCrypt hashes in DB; active status |
| T02 | Bank Teller | Register two customers; create one account each | Distinct customer IDs, unique account numbers; balances start at zero |
| T03 | Bank Teller | Credit Account A ₦100,000 | New balance ₦100,000; one receipt/ledger entry; email queued |
| T04 | Two Bank Tellers | Concurrently debit ₦80,000 each from A | Exactly one succeeds; final balance ₦20,000, one rejection |
| T05 | Bank Teller | Transfer ₦10,000 A → B | A ₦10,000; B ₦10,000; matching ledger; both emails queued |
| T06 | Bank Teller | Attempt transfer to closed/frozen B | Transaction fails; neither balance changes |
| T07 | Bank Teller | Replay identical request ID | No duplicate financial movement or record |
| T08 | Bank Teller + Supervisor | Submit transfer above limit; approve as different employee | Pending → Approved; balance changes only on approval |
| T09 | Supervisor | Reject restricted transaction | Rejected status; balances unchanged |
| T10 | Customer Care Officer | Open, assign, respond and escalate complaint | Ticket/history/audit; supervisor-only escalated handling |
| T11 | Customer Care Officer | Attempt credit/debit through direct service | Authorization refused (not just hidden button) |
| T12 | SMTP sender | Create account; post transaction; update ticket | Correct recipients; masked account numbers; successful delivery statuses |
| T13 | SMTP unavailable | Commit a valid transaction | Money posting remains committed; failed/queued email retained |
| T14 | Administrator / Supervisor | Open reports; filter two UTC dates | Totals match completed records; pending counts and recent table correct |
| T15 | Administrator | View audit tab; teller tries to access reports | Admin sees audit; teller is rejected by service RBAC |

## Evidence capture

Capture screenshots showing only **fictional** names, masked account numbers and non-secret application output. Save the build result, Atlas collection/index listing, approved/rejected request states, balances before/after concurrency, transaction references, Gmail notification records, complaints screen and date-filtered operational reports.

## Known limitations to disclose

- No server-side API: direct Atlas credentials in the user's runtime are inherently extractable; application RBAC cannot substitute for true server-side identity enforcement.
- Financial email delivery is asynchronous and can occasionally generate a duplicate email after an uncertain SMTP response; no duplicate financial posting should occur.
- Live count metrics are not an atomic point-in-time snapshot across MongoDB collections.
- Submission must not claim tests have passed unless the actual output or evidence is available.
