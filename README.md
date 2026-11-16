# Secure Desktop Banking & Customer Care

**Educational simulation only — fictional customers and simulated funds.** Built against the supplied 21-page assignment (deadline **17 November 2026**).

## PR #1 — runnable foundation (this branch)

- Java 21 + JavaFX desktop window; asynchronous employee login screen and basic authenticated dashboard.
- Maven build; MongoDB Atlas sync Java driver; reusable client; index initializer for the ten required collections.
- Secure employee authentication using BCrypt, active-user checks and server-side role/permission mapping.
- One-time administrator seeding via environment variables (never hardcoded credentials).
- Employee login audit records (success/failure), input validation and exact money parsing utilities.
- JUnit tests, CI workflow, .gitignore, architecture/PR plan, and strict secret handling.

**Not implemented in PR #1:** employee UI management, customers, accounts, transactions, ledger operations, transfers, approvals, customer care, SMTP, reports. Do **not** mark the assignment complete based on this foundation.

## Prerequisites

- **JDK 21** (not just JRE), **Maven 3.9+**, MongoDB Atlas **M10 or greater** or another transaction-capable replica set/cluster.
- An Atlas database user with minimal privileges for this *simulation* database.
- Atlas network access configured for the development machine only; no unrestricted production credentials.
- Network access to Maven Central during first build.

## Setup

```bash
cp .env.example .env
# Edit .env: MONGODB_URI, MONGODB_DATABASE, and temporary ADMIN_* values.
set -a; source .env; set +a
mvn -B clean test
mvn -B -q compile exec:java -Dexec.mainClass=com.banking.BootstrapAdmin
unset ADMIN_PASSWORD
mvn javafx:run
```

The UI launches even without Atlas configuration, but login needs `MONGODB_URI` and seeded admin user. **Do not check `.env` into Git**. On Windows PowerShell, set the same keys using `$env:KEY='value'` rather than `source`.

First-run seed exits if an admin with the seed email already exists. Use only the controlled test environment and a unique strong password. It does not overwrite existing credentials.

## Design decisions

- No backend/REST API per the assignment; this imposes a security limitation. Never use live banking data or embed privileged credentials in a distributed desktop binary.
- DB operations run on a JavaFX `Task` and do not block the FX thread.
- Monetary amounts are stored as **integer minor units** (kobo) to avoid `double` rounding; subsequent PRs will use this convention across accounts and ledgers.
- The MongoDB client is shared, and collection indexes are established during bootstrap, not on every query.
- All authorization must be repeated in service code, never depend on hidden UI controls.
- Debit, credit, and transfer functions **must** use conditional atomic updates and MongoDB multi-document transactions (implemented in PR #4).

## GitHub PR workflow

Create an **empty private repository**, upload these files on `main`, and open feature branches as specified in `docs/PR_PLAN.md`. When a fresh repo link is available, the project can be committed and subsequent PRs opened through the connected GitHub account. Avoid putting this educational simulation in one of your unrelated repositories.

## Verification

Run `mvn clean verify` (unit tests, compile and package) and `mvn javafx:run` locally. GitHub Actions performs a Maven verification on pushes and PRs. No live Atlas integration test runs without provided test credentials.
