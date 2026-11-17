# PR #8 — Operational Dashboard, Reports, and Submission Documentation

## Summary

Completes FR-12 (Dashboard and Reports) and the short technical-report deliverable in the supplied coursework specification. Builds on the Gmail notifications branch **after it has been merged**.

## Included

- Read-only dashboard for administrators and supervisors, enforced in `ReportService` through live employee permissions (`VIEW_REPORTS`).
- Current totals: customers, active/frozen accounts, unresolved tickets, pending approvals.
- Filtered, UTC date-bounded completed credit, debit and transfer totals via MongoDB `$match`, `$group` and `$sum` aggregation.
- Up to 30 recent completed transactions in the date range, sorted newest first.
- Administrator-only audit activity tab, with limited date-filtered results and no credential/PII logging.
- Read-only UI queries on background JavaFX `Task` threads; older refreshes cannot overwrite newer ones.
- Additional indexes for report filters and period validation tests.
- `docs/TECHNICAL_REPORT.md` and `docs/SUBMISSION_CHECKLIST.md`.

## Important reporting semantics

- Financial totals count completed operations **created within the selected inclusive UTC dates**, stored in integer kobo.
- Credit total = `CREDIT`; debit total = `DEBIT`; internal transfers are separately reported as `TRANSFER` (so transfers are not double-counted as new deposits or cash withdrawals).
- Customer and account counts, unresolved complaints and pending approvals are **live current-state counts**, not historical balances at the selected date.
- Transaction and audit tables show **up to 30** records. Comprehensive transaction history stays in the Financial transactions module.
- Cross-collection counts are not a point-in-time snapshot; values may change while employees are working.

## Verification before merging

1. `mvn clean verify` completes on JDK 21, including `ReportPeriodTest`.
2. Open a pull request against `main` **after PR #7 is merged** and review GitHub Actions.
3. Manual Atlas integration and UI smoke testing are listed for later in `docs/SUBMISSION_CHECKLIST.md`.
4. Confirm `git status --ignored` shows `.env` ignored, and no credentials are committed.
