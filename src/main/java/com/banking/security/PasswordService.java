package com.banking.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordService {
    private PasswordService() { }

    public static String hash(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Use at least 12 characters for passwords.");
        }
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public static boolean verify(String plaintext, String hash) {
        if (plaintext == null || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }
}
