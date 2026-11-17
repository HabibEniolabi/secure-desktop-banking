package com.banking.security;

import com.banking.models.Role;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAccountAuthorizationTest {
    @Test void tellerCanRegisterAndOpenAccounts() {
        assertTrue(Authorization.can(Role.BANK_TELLER, Permission.MANAGE_CUSTOMERS));
        assertTrue(Authorization.can(Role.BANK_TELLER, Permission.CREATE_ACCOUNTS));
        assertFalse(Authorization.can(Role.BANK_TELLER, Permission.MANAGE_ACCOUNT_STATUS));
    }

    @Test void customerCareCanSearchButNotModifyBalancesOrAccounts() {
        assertTrue(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.VIEW_CUSTOMERS));
        assertTrue(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.VIEW_ACCOUNTS));
        assertFalse(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.MANAGE_CUSTOMERS));
        assertFalse(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.CREATE_ACCOUNTS));
        assertFalse(Authorization.can(Role.CUSTOMER_CARE_OFFICER, Permission.DEBIT));
    }

    @Test void supervisorCanRestrictAccounts() {
        assertTrue(Authorization.can(Role.SUPERVISOR, Permission.MANAGE_ACCOUNT_STATUS));
        assertFalse(Authorization.can(Role.SUPERVISOR, Permission.CREATE_ACCOUNTS));
    }
}
