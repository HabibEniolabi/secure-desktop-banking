package com.banking.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void parsesMinorUnitsExactly() {
        assertEquals(10005L, Money.toMinor("100.05"));
        assertEquals(10000L, Money.toMinor("100"));
        assertEquals("100.05", Money.formatMinor(10005));
    }
    @Test void rejectsInvalidAmounts() {
        for (String input : new String[] {"", "0", "-1", "2.001", "abc", "99999999999999999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> Money.toMinor(input), input);
        }
    }
}
