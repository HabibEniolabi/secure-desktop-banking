package com.banking.notifications;

import com.banking.utils.Money;
import java.util.Date;

/** Plain-text notifications intentionally omit full account numbers and customer personal details. */
public final class EmailTemplate {
    private EmailTemplate() { }

    public record Message(String subject, String body) { }

    public static Message render(String event, String accountNumber, Long amountMinor,
                                 String reference, String ticketNumber, String ticketStatus,
                                 Date occurredAt) {
        String account = mask(accountNumber);
        String when = occurredAt == null ? "" : "\nDate (UTC): " + occurredAt.toInstant();
        String amount = amountMinor == null ? "" : "NGN " + Money.formatMinor(amountMinor);
        String ref = reference == null ? "" : reference;
        String ticket = ticketNumber == null ? "" : ticketNumber;
        return switch (event) {
            case "ACCOUNT_CREATED" -> new Message("Banking simulation: account created",
                "An account ending " + account + " was created." + when + "\nThis is an educational simulation. No real funds are involved.");
            case "CREDIT" -> new Message("Banking simulation: credit confirmation",
                amount + " was credited to account ending " + account + ".\nReference: " + ref + when + "\nThis is a simulated transaction.");
            case "DEBIT" -> new Message("Banking simulation: debit confirmation",
                amount + " was debited from account ending " + account + ".\nReference: " + ref + when + "\nThis is a simulated transaction.");
            case "TRANSFER_SENT" -> new Message("Banking simulation: transfer debit",
                amount + " was transferred out of account ending " + account + ".\nReference: " + ref + when + "\nThis is a simulated transaction.");
            case "TRANSFER_RECEIVED" -> new Message("Banking simulation: transfer credit",
                amount + " was transferred into account ending " + account + ".\nReference: " + ref + when + "\nThis is a simulated transaction.");
            case "TICKET_CREATED" -> new Message("Banking simulation: complaint registered",
                "Your support ticket " + ticket + " has been registered with status OPEN." + when);
            case "TICKET_STATUS_CHANGED" -> new Message("Banking simulation: complaint updated",
                "Your support ticket " + ticket + " now has status " + safeStatus(ticketStatus) + "." + when);
            default -> throw new IllegalArgumentException("Unknown notification type.");
        };
    }

    static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String safeStatus(String status) {
        if (status == null || !java.util.Set.of("OPEN", "IN_PROGRESS", "ESCALATED", "RESOLVED").contains(status))
            throw new IllegalArgumentException("Invalid ticket status.");
        return status.replace('_', ' ');
    }
}
