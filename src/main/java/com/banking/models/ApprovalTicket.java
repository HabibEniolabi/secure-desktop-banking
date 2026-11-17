package com.banking.models;

import java.time.Instant;

/** Immutable approval request projection; never includes mutable account balances. */
public record ApprovalTicket(String approvalId, String requestId, TransactionType type,
                             String sourceAccountNumber, String destinationAccountNumber,
                             long amountMinor, String initiatedBy, String status,
                             String reviewedBy, String transactionReference,
                             Instant createdAt, Instant decidedAt) { }
