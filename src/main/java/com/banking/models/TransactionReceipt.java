package com.banking.models;

import java.time.Instant;

/** Confirmed committed transaction. Null balance fields mean that account was not involved. */
public record TransactionReceipt(String reference, String requestId, TransactionType type,
                                 String sourceAccountNumber, String destinationAccountNumber,
                                 long amountMinor, Long sourceBalanceAfterMinor,
                                 Long destinationBalanceAfterMinor, String employeeId, Instant createdAt) { }
