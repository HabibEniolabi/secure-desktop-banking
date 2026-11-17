package com.banking.notifications;

import java.util.Optional;

/** Gmail SMTP secrets are loaded only from the process environment. */
public record GmailConfig(String username, String appPassword) {
    public GmailConfig {
        if (username == null || username.isBlank() || !username.contains("@"))
            throw new IllegalArgumentException("SMTP_USERNAME must be an email address.");
        if (appPassword == null || appPassword.isBlank())
            throw new IllegalArgumentException("SMTP_APP_PASSWORD is required.");
        username = username.trim();
        appPassword = appPassword.replace(" ", "").trim();
    }

    public static Optional<GmailConfig> fromEnvironment() {
        String username = System.getenv("SMTP_USERNAME");
        String password = System.getenv("SMTP_APP_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank())
            return Optional.empty();
        return Optional.of(new GmailConfig(username, password));
    }
}
