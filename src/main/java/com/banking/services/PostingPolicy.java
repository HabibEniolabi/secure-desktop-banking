package com.banking.services;

import com.banking.utils.Money;

/** Restricted transfers cannot bypass supervisor approvals (to be implemented in next PR). */
public final class PostingPolicy {
    private final long tellerLimitMinor;

    public PostingPolicy(long tellerLimitMinor) {
        if (tellerLimitMinor <= 0) throw new IllegalArgumentException("Approval limit must be positive.");
        this.tellerLimitMinor = tellerLimitMinor;
    }

    public static PostingPolicy fromEnvironment() {
        String amount = System.getenv().getOrDefault("BANKING_TELLER_LIMIT_NGN", "1000000.00");
        return new PostingPolicy(Money.toMinor(amount));
    }

    public long tellerLimitMinor() { return tellerLimitMinor; }

    public void requireWithinLimit(long amountMinor) {
        if (amountMinor > tellerLimitMinor) throw new IllegalArgumentException(
            "Transaction exceeds the teller limit and requires supervisor approval. " +
            "Pending approvals are not yet implemented; no funds have moved.");
    }
}
