package com.banking.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Amounts are stored as signed 64-bit integer KOBO (minor units). Never use double. */
public final class Money {
    private Money() { }

    public static long toMinor(String majorAmount) {
        if (majorAmount == null || majorAmount.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        try {
            BigDecimal naira = new BigDecimal(majorAmount.trim()).setScale(2, RoundingMode.UNNECESSARY);
            long minor = naira.movePointRight(2).longValueExact();
            if (minor <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            return minor;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Enter a valid amount with at most two decimal places.", e);
        }
    }

    public static String formatMinor(long minor) {
        return BigDecimal.valueOf(minor, 2).setScale(2).toPlainString();
    }
}
