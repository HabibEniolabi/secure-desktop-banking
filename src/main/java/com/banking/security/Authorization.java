package com.banking.security;

import com.banking.models.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Invoke this in SERVICES as well as controllers; hiding buttons is not security. */
public final class Authorization {
    private static final Map<Role, EnumSet<Permission>> RULES = new EnumMap<>(Role.class);
    static {
        RULES.put(Role.ADMINISTRATOR, EnumSet.of(
            Permission.MANAGE_EMPLOYEES, Permission.VIEW_REPORTS, Permission.VIEW_AUDIT_LOGS));
        RULES.put(Role.BANK_TELLER, EnumSet.of(
            Permission.MANAGE_CUSTOMERS, Permission.VIEW_CUSTOMERS, Permission.CREATE_ACCOUNTS, Permission.VIEW_ACCOUNTS,
            Permission.CREDIT, Permission.DEBIT, Permission.TRANSFER, Permission.VIEW_TRANSACTIONS));
        RULES.put(Role.CUSTOMER_CARE_OFFICER, EnumSet.of(
            Permission.VIEW_CUSTOMERS, Permission.VIEW_ACCOUNTS, Permission.VIEW_TRANSACTIONS, Permission.MANAGE_TICKETS));
        RULES.put(Role.SUPERVISOR, EnumSet.of(
            Permission.VIEW_CUSTOMERS, Permission.VIEW_ACCOUNTS, Permission.MANAGE_ACCOUNT_STATUS,
            Permission.VIEW_TRANSACTIONS, Permission.APPROVE_RESTRICTED_TRANSACTIONS,
            Permission.MANAGE_TICKETS, Permission.VIEW_REPORTS));
    }
    private Authorization() { }

    public static boolean can(Role role, Permission permission) {
        return role != null && permission != null && RULES.getOrDefault(role, EnumSet.noneOf(Permission.class)).contains(permission);
    }

    public static void require(Role role, Permission permission) {
        if (!can(role, permission)) {
            throw new SecurityException("You are not authorized to perform this operation.");
        }
    }
}
