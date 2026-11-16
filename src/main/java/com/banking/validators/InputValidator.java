package com.banking.validators;

import java.time.LocalDate;
import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private InputValidator() { }

    public static String required(String raw, String field) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return raw.trim();
    }
    public static String email(String raw) {
        String value = required(raw, "Email");
        if (!EMAIL.matcher(value).matches()) throw new IllegalArgumentException("Invalid email address.");
        return value.toLowerCase(java.util.Locale.ROOT);
    }
    public static void dateOfBirth(LocalDate date) {
        if (date == null || !date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth must be in the past.");
        }
    }
}
