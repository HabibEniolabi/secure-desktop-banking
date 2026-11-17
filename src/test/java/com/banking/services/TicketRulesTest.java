package com.banking.services;

import com.banking.models.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketRulesTest {
    @Test void referenceRequiredForFinancialComplaint() {
        assertThrows(IllegalArgumentException.class, () ->
            TicketRules.checkCategory(TicketCategory.INCORRECT_DEBIT, null));
        assertDoesNotThrow(() -> TicketRules.checkCategory(TicketCategory.GENERAL_INQUIRY, null));
    }

    @Test void longOrEmptyMessagesRejected() {
        assertThrows(IllegalArgumentException.class, () -> TicketRules.text("   ", "Response", 3000));
        assertThrows(IllegalArgumentException.class, () -> TicketRules.text("X".repeat(3001), "Response", 3000));
        assertEquals("Hello", TicketRules.text(" Hello ", "Response", 3000));
    }

    @Test void onlySupervisorCanLeaveEscalatedStatus() {
        assertThrows(SecurityException.class, () -> TicketRules.checkTransition(
            TicketStatus.ESCALATED, TicketStatus.RESOLVED, Role.CUSTOMER_CARE_OFFICER));
        assertDoesNotThrow(() -> TicketRules.checkTransition(
            TicketStatus.ESCALATED, TicketStatus.RESOLVED, Role.SUPERVISOR));
    }

    @Test void resolvedTicketsAreImmutable() {
        assertThrows(IllegalArgumentException.class, () -> TicketRules.checkTransition(
            TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS, Role.SUPERVISOR));
        assertThrows(IllegalArgumentException.class, () -> TicketRules.checkTransition(
            TicketStatus.OPEN, TicketStatus.OPEN, Role.SUPERVISOR));
    }
}
