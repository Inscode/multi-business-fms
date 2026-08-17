package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How a customer has actually behaved about paying, per business.
 *
 * <p>Per business on purpose. A shop can be reliable on stationery and slow on Rainco —
 * different reps, different order sizes, different habits — and one blended figure hides
 * exactly the split worth knowing before extending credit again.
 *
 * <p>Every figure is derived from bills, payments and returns as they stand. Nothing is
 * stored, so nothing can go stale or disagree with the ledger.
 */
@Data
@Builder
public class CustomerHealthResponse {

    private Long customerId;
    private String customerName;
    private String area;
    private String phone;
    private String tier;

    /** One entry per business the customer has ever bought from. */
    private List<BusinessHealth> businesses;

    /**
     * The worst rating across the businesses, so a list can be sorted and a single
     * badge shown without pretending the businesses agree.
     */
    private String overallRating;

    @Data
    @Builder
    public static class BusinessHealth {

        private String business;

        /** GOOD, WATCH or CAREFUL. */
        private String rating;

        /**
         * Why it came out that way, in words.
         *
         * <p>Given because a score nobody can take apart gets ignored the first time it
         * disagrees with someone's judgement, and the judgement is usually about a
         * specific thing that happened.
         */
        private List<String> reasons;

        // ── How long they take ──────────────────────────────────────────────
        /** Mean days from bill date to the payment that settled it. Null if none have. */
        private Integer avgDaysToSettle;
        /** The same over the last six months, for the trend rather than the history. */
        private Integer avgDaysToSettleRecent;
        /** The longest they have ever taken to settle one. */
        private Integer worstDaysToSettle;
        /** How many settled bills the averages rest on — a mean of two proves nothing. */
        private int settledBillCount;

        // ── What is open right now ──────────────────────────────────────────
        private BigDecimal currentOutstanding;
        private int openBillCount;
        /** Age of the oldest unpaid bill, in days. */
        private Integer oldestOpenDays;
        /** Of what is open, how much is past the 45-day mark. */
        private BigDecimal overdueAmount;

        // ── Where it went wrong before ──────────────────────────────────────
        private int bouncedChequeCount;
        private LocalDate lastBouncedChequeDate;
        /** Instalment payments — habitually paying in pieces is a signal of its own. */
        private int partialPaymentCount;

        // ── Returns ─────────────────────────────────────────────────────────
        /** Approved damage credit as a share of everything invoiced, in percent. */
        private BigDecimal damageReturnPct;
        private BigDecimal damageReturnAmount;

        // ── The relationship ────────────────────────────────────────────────
        private BigDecimal totalBilled;
        private BigDecimal totalPaid;
        private int billCount;
        private LocalDate firstBillDate;
        private LocalDate lastBillDate;
        /** Days since the last bill — a customer who stopped buying is its own answer. */
        private Integer daysSinceLastBill;
    }
}
