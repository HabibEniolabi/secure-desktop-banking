# PR #1: Initialize JavaFX banking app and secure employee login

## Summary

Introduces a buildable project foundation for the course's Secure Desktop Banking and Customer Care simulation. This PR deliberately does **not** claim the project is complete.

## Changes

- Java 21, JavaFX, Maven, MongoDB Atlas sync driver, BCrypt, Angus Mail dependency, JUnit tests.
- Asynchronous JavaFX sign-in screen and authenticated dashboard placeholder.
- Environment-only Atlas configuration and reusable database connection.
- Index bootstrap for the ten required MongoDB collections.
- Administrator bootstrap CLI from environment secrets.
- Employee password hash verification, inactive account rejection, role permissions and login audit logging.
- Exact minor-unit money parsing and input validation utilities.
- GitHub Actions verification, documentation, and `.gitignore` for credentials.

## Testing

- `mvn clean verify` and `mvn javafx:run` must be run on a machine with Maven and the dependencies installed.
- Offline Java 21 smoke test of `Money`, `InputValidator`, and `Authorization` **passed** in the preparation environment.
- Atlas integration/login is **not verified** until `MONGODB_URI` is supplied; no credentials are included.

## Next PRs

Employee management, customer/account CRUD, concurrency-safe financial operations, complaints, email outbox, reports and final hardening.

## Risk

No real customer or financial data. A direct-DB desktop application is suitable only for this controlled simulation, never a production bank.
