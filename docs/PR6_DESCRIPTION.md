# PR #6 — Customer Care & Complaint Management

## Scope

- Customer care officers and supervisors can register and search complaints.
- Tickets use unique `TKT-` identifiers, categories, statuses, customer/account linkage, and optional transaction references.
- Financial complaints require transaction references; references are checked against the account.
- Complaint ownership can be assigned to active customer care officers. Officers may self-assign; supervisors may assign other officers.
- Status transitions support Open, In Progress, Escalated, and Resolved. Only supervisors can change escalated tickets.
- Responses are stored in `ticket_messages` and shown chronologically.
- Ticket changes, audit events, and `email_notifications` outbox events commit together in MongoDB transactions.
- JavaFX operations use background tasks to preserve interface responsiveness.

## Security and limitations

- This remains a fictional educational simulation with no production customer data.
- Service operations revalidate permissions against the current employee record.
- MongoDB must support multi-document transactions.
- Pending notification records do not send emails; Gmail SMTP delivery comes in the next module.
- Only the first 100 message responses are displayed in the initial history view (repository supports pagination).
- Ticket search currently uses exact ticket/customer/account/transaction identifiers, not free-text or partial search.

## Verification

Run `mvn clean verify`. This runs `TicketRulesTest` as well as existing tests.
Build and database transaction integration should also be checked on a transaction-capable Atlas cluster.
