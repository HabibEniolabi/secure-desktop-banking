# PR #2 — Employee management (FR-02)

## Included
- Administrator-only employee creation, search, profile updates and active status control.
- Assign one of the four required roles.
- BCrypt hashes new employee passwords; never return hashes in the UI employee table.
- Unique employee email and username protected by MongoDB indexes from PR #1.
- Service-level authorization re-checks live employee role and active status before each operation.
- Audit records for employee creation and updates.
- JavaFX management screen with background database tasks.

## Limitations / follow-ups
- Profile management does not yet provide password reset or paginated next/previous controls (the repository exposes page-based queries).
- Administrators cannot disable or demote themselves; a last-active-admin check blocks sequential changes but is not an atomic multi-admin quorum guarantee.
- Concurrent updates to the same employee currently use last-write-wins semantics.
- Verify against an Atlas test cluster with a seeded administrator; no live integration credentials are included.

## Manual acceptance checklist
1. Sign in using the seeded administrator; open **Manage employees**.
2. Create a teller; verify it appears in the table and can log in.
3. Attempt a duplicate email/username; verify the creation is rejected.
4. Edit the teller's name and role; verify persistence after a refresh.
5. Deactivate the teller; verify login is rejected and existing-session operations are also refused.
6. Check a teller session does not show employee management and its service operations reject teller identity.
7. Verify password hashes, not cleartext passwords, are stored in `employees`.
8. Review `audit_logs` for employee creation and update events.

## Local commands
```bash
mvn clean verify
mvn javafx:run
```
