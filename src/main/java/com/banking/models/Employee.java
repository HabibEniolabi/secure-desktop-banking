package com.banking.models;

/** The hash is available only to the authentication layer, never shown on UI. */
public record Employee(String id, String fullName, String email,
                       String username, String passwordHash, Role role, boolean active) {
}
