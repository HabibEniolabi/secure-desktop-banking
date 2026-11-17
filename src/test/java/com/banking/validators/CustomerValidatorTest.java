package com.banking.validators;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class CustomerValidatorTest {
    private static final LocalDate PAST = LocalDate.of(2000, 2, 29);

    @Test void normalizesEmailAndPhone() {
        var data = CustomerValidator.validate("  Ada Example  ", " ADA@EXAMPLE.COM ",
            "+234 (801) 234-5678", "  10 Example Street  ", PAST);
        assertEquals("Ada Example", data.fullName());
        assertEquals("ada@example.com", data.email());
        assertEquals("+2348012345678", data.phone());
    }

    @Test void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
            () -> CustomerValidator.validate(" ", "ada@example.com", "08012345678", "Address", PAST));
        assertThrows(IllegalArgumentException.class,
            () -> CustomerValidator.validate("Ada", "invalid", "08012345678", "Address", PAST));
        assertThrows(IllegalArgumentException.class,
            () -> CustomerValidator.validate("Ada", "ada@example.com", "xyz", "Address", PAST));
        assertThrows(IllegalArgumentException.class,
            () -> CustomerValidator.validate("Ada", "ada@example.com", "08012345678", "Address", LocalDate.now()));
        assertThrows(IllegalArgumentException.class,
            () -> CustomerValidator.validate("Ada", "ada@example.com", "08012345678", "  ", PAST));
    }
}
