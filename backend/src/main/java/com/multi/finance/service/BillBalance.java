package com.multi.finance.service;

import com.multi.finance.entity.Bill;
import com.multi.finance.enums.BillStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The one place a bill's balance is worked out.
 *
 * <p>A bill is worth what it was invoiced for, less anything the customer sent back:
 * <pre>payable = total_amount - returns_total</pre>
 * and the balance is what is left of that after payments. Returns therefore never touch
 * {@code total_amount} — reversing one is a matter of recomputing this, not of adding
 * money back and hoping the arithmetic survives a second edit.
 *
 * <p>Every site that moves a payment or a return funnels through here so the three
 * stored fields — {@code balanceRemaining}, {@code fullyPaid}, {@code amountPaid} —
 * can never disagree with each other.
 */
public final class BillBalance {

    private BillBalance() {}

    /** What the customer actually owes on this bill, before payments. */
    public static BigDecimal payable(Bill bill) {
        BigDecimal total = nz(bill.getTotalAmount());
        BigDecimal returns = nz(bill.getReturnsTotal());
        BigDecimal payable = total.subtract(returns);
        // Returns worth more than the bill would mean crediting the customer for goods
        // they were never charged for; floor it rather than invent a negative bill.
        return payable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : payable;
    }

    /**
     * Recomputes balance and paid-off state from the bill's own figures. Call after
     * changing {@code amountPaid}, {@code totalAmount} or {@code returnsTotal}.
     */
    public static void recompute(Bill bill) {
        BigDecimal payable = payable(bill);
        BigDecimal paid = nz(bill.getAmountPaid());
        BigDecimal balance = payable.subtract(paid);

        // An overpayment is left visible rather than clamped, because money owed back
        // to the customer is still money that has to be dealt with.
        bill.setBalanceRemaining(balance);
        bill.setFullyPaid(balance.compareTo(BigDecimal.ZERO) <= 0);
        bill.setUpdatedAt(LocalDateTime.now());
    }

    /** Records a payment and recomputes. */
    public static void applyPayment(Bill bill, BigDecimal amount) {
        bill.setAmountPaid(nz(bill.getAmountPaid()).add(nz(amount)));
        recompute(bill);
    }

    /** Takes a payment back off and recomputes. */
    public static void reversePayment(Bill bill, BigDecimal amount) {
        BigDecimal paid = nz(bill.getAmountPaid()).subtract(nz(amount));
        bill.setAmountPaid(paid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : paid);
        recompute(bill);
    }

    /**
     * Moves the bill's status back out of a paid-off state after money was reversed.
     * Kept here so the status and the balance can't be updated independently.
     */
    public static void reopenIfClosed(Bill bill, BillStatus reopenTo) {
        if (bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION) {
            bill.setStatus(reopenTo);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
