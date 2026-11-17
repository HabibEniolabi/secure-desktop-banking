package com.banking.models;

import java.time.Instant;
import java.time.LocalDate;

/** Immutable customer information. All values are fictitious in this simulation. */
public record Customer(String customerId, String fullName, String email, String phone,
                       String address, LocalDate dateOfBirth, Instant createdAt, Instant updatedAt) { }
