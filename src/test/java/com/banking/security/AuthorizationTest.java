package com.banking.security;

import com.banking.models.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationTest {
    @Test void customerCareCannotWithdraw() {
        assertFalse(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.DEBIT));
        assertThrows(SecurityException.class,
            () -> Authorization.require(Role.CUSTOMER_CARE_OFFICER, Permission.DEBIT));
    }
    @Test void tellerCannotApproveOwnOrOtherRequests() {
        assertTrue(Authorization.can(Role.BANK_TELLER, Permission.DEBIT));
        assertFalse(Authorization.can(Role.BANK_TELLER, Permission.APPROVE_RESTRICTED_TRANSACTIONS));
    }
    @Test void onlyAdminCanManageEmployees() {
        assertTrue(Authorization.can(Role.ADMINISTRATOR, Permission.MANAGE_EMPLOYEES));
        for (Role role : Role.values()) {
            if (role != Role.ADMINISTRATOR) {
                assertFalse(Authorization.can(role, Permission.MANAGE_EMPLOYEES));
            }
        }
    }
}
