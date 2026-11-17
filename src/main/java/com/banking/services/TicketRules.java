package com.banking.services;

import com.banking.models.TicketCategory;
import com.banking.models.TicketStatus;
import com.banking.models.Role;

/** Side-effect-free ticket policy; independently unit-tested. */
public final class TicketRules {
    private TicketRules() { }

    public static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required.");
        String text = value.trim();
        if (text.length() > maximum)
            throw new IllegalArgumentException(field + " must be at most " + maximum + " characters.");
        return text;
    }

    public static void checkCategory(TicketCategory category, String transactionReference) {
        if (category == null) throw new IllegalArgumentException("Complaint category is required.");
        if (category == TicketCategory.FAILED_TRANSACTION || category == TicketCategory.INCORRECT_DEBIT
                || category == TicketCategory.UNAUTHORIZED_TRANSACTION) {
            if (transactionReference == null || transactionReference.isBlank())
                throw new IllegalArgumentException("A transaction reference is required for this category.");
        }
    }

    public static void checkTransition(TicketStatus current, TicketStatus target, Role actorRole) {
        if (current == null || target == null || actorRole == null)
            throw new IllegalArgumentException("A valid ticket status and role are required.");
        if (current == TicketStatus.RESOLVED)
            throw new IllegalArgumentException("Resolved tickets cannot be changed.");
        if (current == target) throw new IllegalArgumentException("Ticket already has that status.");
        if (current == TicketStatus.ESCALATED && actorRole != Role.SUPERVISOR)
            throw new SecurityException("Only a supervisor can change an escalated ticket.");
        if (target == TicketStatus.ESCALATED && current != TicketStatus.OPEN
                && current != TicketStatus.IN_PROGRESS)
            throw new IllegalArgumentException("Ticket cannot be escalated from its current status.");
        if (target == TicketStatus.OPEN)
            throw new IllegalArgumentException("A ticket cannot be returned to Open.");
    }
}
