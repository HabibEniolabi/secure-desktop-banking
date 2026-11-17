package com.banking.models;

import java.time.Instant;

/** Amounts are integer kobo; do not use floating point for currency. */
public record BankAccount(String accountNumber, String customerId, long balanceMinor,
                          AccountStatus status, Instant createdAt, Instant updatedAt) { }
