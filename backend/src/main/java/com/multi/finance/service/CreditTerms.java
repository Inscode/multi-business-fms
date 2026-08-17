package com.multi.finance.service;

/**
 * The three day-counts the business runs on, and what each one actually means.
 *
 * <p>They are told apart deliberately. Collapsing them into one "overdue" number is what
 * makes a late-payment report useless: a bill at 50 days is a bill being watched, and a
 * bill at 80 days is money the company has already paid for out of its own pocket, and
 * only one of those is worth ringing someone about.
 */
public final class CreditTerms {

    private CreditTerms() {}

    /**
     * What is stamped on the bill.
     *
     * <p>Set short on purpose — asking for 45 leaves room to be paid at 60 and still be
     * whole. It is a negotiating position, not a deadline, which is why exceeding it is
     * reported but never treated as a breach.
     */
    public static final int SEAL_DAYS = 45;

    /**
     * Where a customer starts being a problem.
     *
     * <p>Judgement rather than arithmetic: past 60 days a customer has stopped treating
     * the credit as credit, and in practice the ones who reach here are the ones who go
     * on to reach 70. This is the line the customer ratings use.
     */
    public static final int DANGER_DAYS = 60;

    /**
     * What the company itself is given to pay the principal.
     *
     * <p>The only hard number of the three. Money collected after this was money the
     * company had already handed over — the sale was funded out of its own cash for the
     * days in between, whatever the customer eventually paid. This is the line that costs
     * something to cross.
     */
    public static final int SUPPLIER_DAYS = 70;

    /** Which band a settled bill's age falls into. */
    public static String bandFor(int days) {
        if (days <= SEAL_DAYS) return "ON_TIME";
        if (days <= DANGER_DAYS) return "WATCH";
        if (days <= SUPPLIER_DAYS) return "LATE";
        return "BEYOND_TERMS";
    }
}
