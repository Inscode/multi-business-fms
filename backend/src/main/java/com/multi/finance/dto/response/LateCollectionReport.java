package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How long the money took to come in, for everything collected in a period.
 *
 * <p>Keyed on the date the payment landed, not the date the bill was raised: the question
 * is what came in this month and how old it was when it did. A report keyed on bill date
 * answers a different question and cannot be reconciled against a bank statement.
 *
 * <p>The point of it is the line at 70 days. The company is given 70 days to pay the
 * principal, so a rupee collected on day 85 was a rupee the company funded for fifteen
 * days out of its own cash. That cost is invisible everywhere else in the system — the
 * bill closes, the balance goes to zero, and nothing records that it closed late.
 */
@Data
@Builder
public class LateCollectionReport {

    private String business;
    private LocalDate from;
    private LocalDate to;

    /** The day-counts the bands are cut at, so the page never hard-codes them. */
    private int sealDays;
    private int dangerDays;
    private int supplierDays;

    // ── The whole period ────────────────────────────────────────────────────
    /** Day-counts for cash, which is due on delivery rather than on credit. */
    private int cashSealDays;
    private int cashDangerDays;
    private int cashSupplierDays;

    private BigDecimal totalCollected;
    private int paymentCount;

    /**
     * Weighted by amount, not by bill. A large invoice paid at 90 days costs the company
     * far more than a small one, and an unweighted mean would rank them the same.
     */
    private Integer avgDaysWeighted;

    /** Collected past 70 days — the part the company had already paid for. */
    private BigDecimal beyondTermsAmount;
    /** That, as a share of everything collected in the period. */
    private BigDecimal beyondTermsPct;

    /** Past 60 days: the danger line, whether or not it reached 70. */
    private BigDecimal pastDangerAmount;
    private BigDecimal pastDangerPct;

    private List<Band> bands;
    private List<CustomerRow> customers;
    private List<PaymentRow> payments;

    @Data
    @Builder
    public static class Band {
        /** ON_TIME, WATCH, LATE or BEYOND_TERMS. */
        private String band;
        private String label;
        private BigDecimal amount;
        private int count;
        /** Share of the period's collections, so the bands can be read without a calculator. */
        private BigDecimal pct;
    }

    /** One customer's contribution to the period's lateness, worst first. */
    @Data
    @Builder
    public static class CustomerRow {
        private Long customerId;
        private String customerName;
        private String area;
        private BigDecimal collected;

        /**
         * Collected in the window between the danger line and the supplier deadline.
         *
         * <p>Disjoint from {@link #beyondTermsAmount} on purpose. They used to nest —
         * past-60 counted everything past-70 did — so the two columns showed the same
         * figure on every customer whose payments were all badly late, which is exactly
         * the customer the report is about.
         */
        private BigDecimal lateAmount;

        /** Collected past the supplier deadline — the money that cost something. */
        private BigDecimal beyondTermsAmount;

        /** The two above together, for anyone wanting the total that ran past terms. */
        private BigDecimal pastDangerAmount;
        private Integer avgDaysWeighted;
        private Integer worstDays;
        private int paymentCount;
    }

    /** A single payment, for the drill-down behind a customer or a band. */
    @Data
    @Builder
    public static class PaymentRow {
        private Long billId;
        private String billNumber;
        private String customerName;
        private String area;
        private LocalDate billDate;
        private LocalDate paymentDate;
        private int days;
        private String band;
        private BigDecimal amount;
        private String paymentType;
        /** CASH or CREDIT — the terms the days were judged against. */
        private String billType;
    }
}
