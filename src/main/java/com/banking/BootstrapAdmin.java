package com.banking;

import com.banking.config.AppConfig;
import com.banking.config.MongoContext;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.PasswordService;
import com.banking.validators.InputValidator;

/** Explicit CLI only. Do not ship a default password or admin backdoor. */
public final class BootstrapAdmin {
    private BootstrapAdmin() { }

    public static void main(String[] args) {
        String name = InputValidator.required(System.getenv("ADMIN_NAME"), "ADMIN_NAME");
        String email = InputValidator.email(System.getenv("ADMIN_EMAIL"));
        String password = InputValidator.required(System.getenv("ADMIN_PASSWORD"), "ADMIN_PASSWORD");
        try (MongoContext mongo = new MongoContext(AppConfig.fromEnvironment())) {
            mongo.ensureIndexes();
            new EmployeeRepository(mongo.database()).seedAdministrator(name, email, PasswordService.hash(password));
            System.out.println("Initial administrator created. Unset ADMIN_PASSWORD now.");
        }
    }
}
