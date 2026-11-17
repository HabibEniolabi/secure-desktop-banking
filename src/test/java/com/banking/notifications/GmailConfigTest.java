package com.banking.notifications;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GmailConfigTest {
    @Test void removesCosmeticSpacesFromGoogleAppPassword() {
        GmailConfig config = new GmailConfig("test@gmail.com", "abcd efgh ijkl mnop");
        assertEquals("abcdefghijklmnop", config.appPassword());
    }

    @Test void requiresNonemptyCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new GmailConfig("", "app-password"));
        assertThrows(IllegalArgumentException.class, () -> new GmailConfig("test@gmail.com", ""));
    }
}
