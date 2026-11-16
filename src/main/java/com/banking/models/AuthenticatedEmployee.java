package com.banking.models;

/** Safe session identity without password hashes. */
public record AuthenticatedEmployee(String id, String fullName, String email, Role role) {
}
