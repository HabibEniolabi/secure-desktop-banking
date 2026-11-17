package com.banking.models;

import java.time.Instant;

public record SupportTicket(String ticketNumber, String customerId, String accountNumber,
                            String transactionReference, TicketCategory category, TicketStatus status,
                            String description, String assignedOfficerId, String createdBy,
                            Instant createdAt, Instant updatedAt) { }
