package com.banking.notifications;

/** Sender abstraction makes the notification pipeline testable without sending real mail. */
@FunctionalInterface
public interface EmailSender {
    void send(String recipient, EmailTemplate.Message content) throws Exception;
}
