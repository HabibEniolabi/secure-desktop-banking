package com.banking.validators;

import java.time.LocalDate;
import java.util.Locale;

/** Business-layer validation: independent of JavaFX field restrictions. */
public final class CustomerValidator {
    private CustomerValidator() { }

    public static CustomerData validate(String fullName, String email, String phone,
                                        String address, LocalDate dateOfBirth) {
        String name = InputValidator.required(fullName, "Full name");
        if (name.length() > 120) throw new IllegalArgumentException("Full name cannot exceed 120 characters.");
        String mail = InputValidator.email(email);
        if (mail.length() > 254) throw new IllegalArgumentException("Email cannot exceed 254 characters.");
        String normalizedPhone = InputValidator.required(phone, "Phone").replaceAll("[\\s()\\-]", "");
        if (!normalizedPhone.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("Phone must contain 7 to 15 digits, optionally starting with +.");
        }
        String location = InputValidator.required(address, "Residential address");
        if (location.length() > 300) throw new IllegalArgumentException("Address cannot exceed 300 characters.");
        InputValidator.dateOfBirth(dateOfBirth);
        return new CustomerData(name, mail.toLowerCase(Locale.ROOT), normalizedPhone, location, dateOfBirth);
    }

    public record CustomerData(String fullName, String email, String phone,
                               String address, LocalDate dateOfBirth) { }
}
