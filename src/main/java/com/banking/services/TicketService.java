package com.banking.services;

import com.banking.models.*;
import com.banking.repositories.AccountRepository;
import com.banking.repositories.CustomerRepository;
import com.banking.repositories.EmployeeRepository;
import com.banking.repositories.TicketRepository;
import com.banking.security.Permission;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

/** Database-backed RBAC; never trust disabled JavaFX buttons as authorization. */
public final class TicketService {
    private final TicketRepository tickets;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final EmployeeRepository employees;
    private final EmployeeAccess access;
    private final MongoDatabase database;

    public TicketService(TicketRepository tickets, CustomerRepository customers,
                         AccountRepository accounts, EmployeeRepository employees,
                         EmployeeAccess access, MongoDatabase database) {
        this.tickets = tickets; this.customers = customers; this.accounts = accounts;
        this.employees = employees; this.access = access; this.database = database;
    }

    public SupportTicket register(AuthenticatedEmployee actor, String customerId,
                                  String accountNumber, String transactionReference,
                                  TicketCategory category, String description) {
        access.require(actor, Permission.MANAGE_TICKETS);
        String customerKey = TicketRules.text(customerId, "Customer ID", 64);
        String accountKey = TicketRules.text(accountNumber, "Account number", 32);
        String body = TicketRules.text(description, "Complaint description", 3000);
        String txn = transactionReference == null || transactionReference.isBlank()
            ? null : TicketRules.text(transactionReference, "Transaction reference", 128);
        TicketRules.checkCategory(category, txn);
        Customer customer = customers.findById(customerKey)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        BankAccount account = accounts.findByNumber(accountKey)
            .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (!account.customerId().equals(customer.customerId()))
            throw new IllegalArgumentException("Account does not belong to this customer.");
        if (txn != null) {
            Document record = database.getCollection("transactions").find(eq("reference", txn)).first();
            if (record == null || !(accountKey.equals(record.getString("accountNumber"))
                    || accountKey.equals(record.getString("sourceAccountNumber"))
                    || accountKey.equals(record.getString("destinationAccountNumber"))))
                throw new IllegalArgumentException("Transaction reference does not match the selected account.");
        }
        return tickets.create("TKT-" + UUID.randomUUID().toString().toUpperCase(),
            customerKey, accountKey, txn, category, body, actor.id(), customer.email());
    }

    public List<SupportTicket> search(AuthenticatedEmployee actor, String query,
                                      TicketStatus status, int page, int size) {
        access.require(actor, Permission.MANAGE_TICKETS);
        CustomerService.validatePage(page, size);
        if (query != null && query.length() > 128)
            throw new IllegalArgumentException("Search term is too long.");
        return tickets.search(query, status, page, size);
    }

    public List<TicketMessage> history(AuthenticatedEmployee actor, String ticketNumber,
                                        int page, int size) {
        access.require(actor, Permission.MANAGE_TICKETS);
        CustomerService.validatePage(page, size);
        get(ticketNumber);
        return tickets.messages(ticketNumber, page, size);
    }

    public SupportTicket respond(AuthenticatedEmployee actor, String ticketNumber, String response) {
        Role role = currentRole(actor);
        SupportTicket ticket = get(ticketNumber);
        if (ticket.status() == TicketStatus.RESOLVED)
            throw new IllegalArgumentException("Resolved tickets cannot receive responses.");
        String body = TicketRules.text(response, "Response", 3000);
        return tickets.update(ticketNumber, ticket.status(), ticket.status(),
            ticket.assignedOfficerId(), actor.id(), body, recipient(ticket.customerId()));
    }

    public SupportTicket setStatus(AuthenticatedEmployee actor, String ticketNumber,
                                    TicketStatus status, String response) {
        Role role = currentRole(actor);
        SupportTicket ticket = get(ticketNumber);
        TicketRules.checkTransition(ticket.status(), status, role);
        String body = TicketRules.text(response, "Status response", 3000);
        return tickets.update(ticketNumber, ticket.status(), status,
            ticket.assignedOfficerId(), actor.id(), body, recipient(ticket.customerId()));
    }

    public SupportTicket assign(AuthenticatedEmployee actor, String ticketNumber, String officerId) {
        Role role = currentRole(actor);
        String officerKey = TicketRules.text(officerId, "Officer ID", 80);
        Employee officer = employees.findById(officerKey)
            .orElseThrow(() -> new IllegalArgumentException("Customer care officer not found."));
        if (!officer.active() || officer.role() != Role.CUSTOMER_CARE_OFFICER)
            throw new IllegalArgumentException("Assignee must be an active customer care officer.");
        if (role != Role.SUPERVISOR && !actor.id().equals(officerKey))
            throw new SecurityException("Only supervisors can assign complaints to another officer.");
        SupportTicket ticket = get(ticketNumber);
        if (ticket.status() == TicketStatus.RESOLVED)
            throw new IllegalArgumentException("Cannot assign a resolved ticket.");
        if (ticket.status() == TicketStatus.ESCALATED && role != Role.SUPERVISOR)
            throw new SecurityException("Escalated tickets are managed by supervisors.");
        if (officerKey.equals(ticket.assignedOfficerId()))
            return ticket;
        return tickets.update(ticketNumber, ticket.status(), ticket.status(), officerKey,
            actor.id(), null, recipient(ticket.customerId()));
    }

    private Role currentRole(AuthenticatedEmployee actor) {
        access.require(actor, Permission.MANAGE_TICKETS);
        return employees.findById(actor.id()).orElseThrow(
            () -> new SecurityException("Employee no longer exists.")).role();
    }

    private SupportTicket get(String ticketNumber) {
        String key = TicketRules.text(ticketNumber, "Ticket number", 64);
        return tickets.findByNumber(key).orElseThrow(
            () -> new IllegalArgumentException("Ticket not found."));
    }

    private String recipient(String customerId) {
        return customers.findById(customerId).orElseThrow(
            () -> new IllegalArgumentException("Customer not found.")).email();
    }
}
