package com.banking.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostingPolicyTest {
    @Test void blocksUnapprovedRestrictedAmounts() {
        PostingPolicy policy = new PostingPolicy(100_00L);
        assertDoesNotThrow(() -> policy.requireWithinLimit(100_00L));
        assertThrows(IllegalArgumentException.class, () -> policy.requireWithinLimit(100_01L));
    }

    @Test void requiresConfiguredPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new PostingPolicy(0));
    }
}
