package com.banking.models;

import com.banking.utils.Money;
import java.util.UUID;

/** One immutable, client-generated idempotent command; reuse requestId on every retry. */
public record BankingRequest(String requestId, TransactionType type, String sourceAccountNumber,
                             String destinationAccountNumber, long amountMinor) {
    public BankingRequest {
        if (requestId == null) throw new IllegalArgumentException("Request ID is required.");
        try { requestId = UUID.fromString(requestId.trim()).toString(); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Request ID must be a UUID.", ex); }
        if (type == null) throw new IllegalArgumentException("Transaction type is required.");
        if (amountMinor <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        switch (type) {
            case CREDIT -> {
                if (sourceAccountNumber != null) throw new IllegalArgumentException("Credit must not have a source account.");
                destinationAccountNumber = validateAccount(destinationAccountNumber);
            }
            case DEBIT -> {
                sourceAccountNumber = validateAccount(sourceAccountNumber);
                if (destinationAccountNumber != null) throw new IllegalArgumentException("Debit must not have a destination account.");
            }
            case TRANSFER -> {
                sourceAccountNumber = validateAccount(sourceAccountNumber);
                destinationAccountNumber = validateAccount(destinationAccountNumber);
                if (sourceAccountNumber.equals(destinationAccountNumber))
                    throw new IllegalArgumentException("Source and destination accounts must differ.");
            }
        }
    }

    private static String validateAccount(String value) {
        if (value == null || !value.trim().matches("[0-9]{10}"))
            throw new IllegalArgumentException("Enter a 10-digit account number.");
        return value.trim();
    }

    public static BankingRequest credit(String id, String destination, String naira) {
        return new BankingRequest(id, TransactionType.CREDIT, null, destination, Money.toMinor(naira));
    }
    public static BankingRequest debit(String id, String source, String naira) {
        return new BankingRequest(id, TransactionType.DEBIT, source, null, Money.toMinor(naira));
    }
    public static BankingRequest transfer(String id, String source, String destination, String naira) {
        return new BankingRequest(id, TransactionType.TRANSFER, source, destination, Money.toMinor(naira));
    }
}
