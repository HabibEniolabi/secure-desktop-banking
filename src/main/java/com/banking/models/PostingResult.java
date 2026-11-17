package com.banking.models;

/** A request may be financially completed or only queued; never conflate the two. */
public record PostingResult(boolean pendingApproval, String approvalId,
                            TransactionReceipt receipt) {
    public static PostingResult pending(String approvalId) {
        return new PostingResult(true, approvalId, null);
    }
    public static PostingResult completed(TransactionReceipt receipt) {
        return new PostingResult(false, null, java.util.Objects.requireNonNull(receipt));
    }
}
