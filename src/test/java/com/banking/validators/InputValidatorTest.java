package com.banking.validators;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {
    @Test void rejectsEmptyAndMalformedInputs() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.required("   ", "Name"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.email("not-an-email"));
        assertEquals("test@example.com", InputValidator.email("TEST@EXAMPLE.COM"));
    }
    @Test void birthDateMustBeInPast() {
        assertThrows(IllegalArgumentException.class, () -> InputValidator.dateOfBirth(LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.dateOfBirth(LocalDate.now().plusDays(1)));
        assertDoesNotThrow(() -> InputValidator.dateOfBirth(LocalDate.of(2000, 1, 1)));
    }
}
