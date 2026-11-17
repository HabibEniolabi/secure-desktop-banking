package com.banking.models;

import java.time.Instant;

public record TicketMessage(String messageId, String ticketNumber,
                            String employeeId, String body, Instant createdAt) { }
