package com.banking.services;

import com.banking.utils.Money;

/** Restricted transactions are placed in supervisor approvals, not executed. */
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
            "Submit the request for supervisor approval instead.");
    }
}
