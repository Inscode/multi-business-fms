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

    // ── Cash ────────────────────────────────────────────────────────────────
    //
    // A cash bill is not credit at all: the money is due when the goods are handed over,
    // so measuring it against the credit run reports a cash sale collected three weeks
    // late as comfortably on time. Rainco is the only business that really sells both —
    // a cash bill elsewhere is a typing slip today, though that may change.

    /** A day or two to reach the office is ordinary; beyond that it is being carried. */
    public static final int CASH_SEAL_DAYS = 7;

    /**
     * Where cash stops being slow and starts being a debt nobody agreed to.
     *
     * <p>Fifteen because the dashboard already calls cash past fifteen days serious, and
     * two different answers to the same question on two screens is worse than either.
     */
    public static final int CASH_DANGER_DAYS = 15;

    /** Past this, cash has been outstanding longer than credit terms would have allowed. */
    public static final int CASH_SUPPLIER_DAYS = 30;

    public static int sealDays(boolean cash)     { return cash ? CASH_SEAL_DAYS : SEAL_DAYS; }
    public static int dangerDays(boolean cash)   { return cash ? CASH_DANGER_DAYS : DANGER_DAYS; }
    public static int supplierDays(boolean cash) { return cash ? CASH_SUPPLIER_DAYS : SUPPLIER_DAYS; }

    /**
     * Which band a settled bill's age falls into, on its own terms.
     *
     * @param cash true for a cash bill, which is due on delivery rather than on credit
     */
    public static String bandFor(int days, boolean cash) {
        if (days <= sealDays(cash))     return "ON_TIME";
        if (days <= dangerDays(cash))   return "WATCH";
        if (days <= supplierDays(cash)) return "LATE";
        return "BEYOND_TERMS";
    }

    /** Credit terms, for callers with no bill type to hand. */
    public static String bandFor(int days) {
        return bandFor(days, false);
    }
}
