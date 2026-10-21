package com.multi.finance.enums;

/**
 * A return's life from the accountant entering it to the money coming off the bill.
 *
 * <p>{@code PENDING} and {@code APPROVED} keep the meanings they had before the goods
 * gate existed, so returns entered under the old flow still read correctly.
 */
public enum ReturnStatus {
    /** Entered by the accountant; the goods have not been confirmed as arrived. */
    PENDING,

    /**
     * The accountant has physically seen what came back — all of it, some of it, or
     * none. Payment is allowed after this point, while the bill credit waits for admin
     * approval.
     */
    GOODS_CONFIRMED,

    /** Admin signed it off; the credit is deducted from what the bill is worth. */
    APPROVED,

    /** Admin refused the claim — nothing comes off the bill. */
    REJECTED,

    /** The goods never arrived. Kept rather than deleted so the claim stays traceable. */
    NOT_RECEIVED,

    /** Reversed by the admin after approval — the credit goes back onto the bill. */
    CANCELLED;

    /** True while the accountant or admin still needs to act on the return. */
    public boolean isOpen() {
        return this == PENDING || this == GOODS_CONFIRMED;
    }

    /** True when the return is currently taking money off the bill. */
    public boolean reducesBill() {
        return this == APPROVED;
    }
}
