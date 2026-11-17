package com.banking.models;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BankingRequestTest {
    private static final String ID = UUID.randomUUID().toString();

    @Test void rejectsInvalidAmountsAndAccounts() {
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.debit(ID, "1234567890", "0"));
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.credit(ID, "1234567890", "-1"));
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.credit(ID, "1234567890", "1.001"));
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.debit(ID, "123", "200.00"));
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.transfer(ID, "1234567890", "1234567890", "10"));
        assertThrows(IllegalArgumentException.class, () -> BankingRequest.credit("invalid", "1234567890", "20"));
    }

    @Test void preservesExactMoneyAndIdempotencyKey() {
        BankingRequest request = BankingRequest.transfer(ID, "1234567890", "0987654321", "1000.75");
        assertEquals(100075L, request.amountMinor());
        assertEquals(ID, request.requestId());
        assertEquals(TransactionType.TRANSFER, request.type());
        assertEquals("0987654321", request.destinationAccountNumber());
    }
}
