package com.banking.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    @Test void hashAndVerifyWithoutStoringPlaintext() {
        String password = "Correct-Horse-987-!";
        String hash1 = PasswordService.hash(password);
        String hash2 = PasswordService.hash(password);
        assertNotEquals(password, hash1);
        assertNotEquals(hash1, hash2, "BCrypt must use randomized salt");
        assertTrue(PasswordService.verify(password, hash1));
        assertFalse(PasswordService.verify("wrong-password", hash1));
        assertFalse(PasswordService.verify(password, null));
    }
    @Test void rejectsWeakPassword() {
        assertThrows(IllegalArgumentException.class, () -> PasswordService.hash("short"));
    }
}
