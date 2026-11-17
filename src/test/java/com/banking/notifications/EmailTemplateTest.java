package com.banking.notifications;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateTest {
    @Test void masksFullAccountNumber() {
        var mail = EmailTemplate.render("CREDIT", "1234567890", 125050L, "TXN-SAMPLE", null, null, new java.util.Date(0));
        assertTrue(mail.body().contains("****7890"));
        assertTrue(mail.body().contains("NGN 1250.50"));
        assertTrue(mail.body().contains("TXN-SAMPLE"));
        assertTrue(mail.body().contains("1970-01-01"));
        assertFalse(mail.body().contains("1234567890"));
    }

    @Test void identifiesTicketStatusWithoutPersonalDetails() {
        var mail = EmailTemplate.render("TICKET_STATUS_CHANGED", null, null, null, "TKT-123", "IN_PROGRESS", new java.util.Date(0));
        assertTrue(mail.body().contains("TKT-123"));
        assertTrue(mail.body().contains("IN PROGRESS"));
    }

    @Test void doesNotAllowUnexpectedTypesOrStatuses() {
        assertThrows(IllegalArgumentException.class,
            () -> EmailTemplate.render("UNKNOWN", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> EmailTemplate.render("TICKET_STATUS_CHANGED", null, null, null, "TKT-1", "INVALID", null));
    }

    @Test void supportsAllBankingEvents() {
        for (String event : new String[]{"CREDIT", "DEBIT", "TRANSFER_SENT", "TRANSFER_RECEIVED"}) {
            var mail = EmailTemplate.render(event, "1234567890", 10000L, "TXN-1", null, null, null);
            assertFalse(mail.subject().isBlank());
        }
    }
}
