package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Forward cash-flow view: what is committed to arrive versus what falls due,
 * over a chosen horizon. Deliberately forward-looking — money already banked
 * is not counted, only what is still to come.
 */
@Data
@Builder
public class CashFlowResponse {
    private LocalDate from;
    private LocalDate to;
    private int horizonDays;

    private List<BusinessCashFlow> businesses;

    // Totals across every business
    private BigDecimal totalIncoming;
    private BigDecimal totalOutgoing;
    private BigDecimal totalNet;
    /** Owed to you but with no date attached — context, never counted in net. */
    private BigDecimal totalUndatedReceivable;
    private BigDecimal totalOverdueOutgoing;
    /** Payable notes with no terms typed — owed, but impossible to place on the timeline. */
    private BigDecimal totalUntermed;
    private int untermedCount;

    @Data
    @Builder
    public static class BusinessCashFlow {
        private String business;
        /** Cheques already in hand, dated inside the window. */
        private BigDecimal chequesIncoming;
        private int chequeCount;
        /** GRN dues plus recorded obligations landing inside the window. */
        private BigDecimal purchasesDue;
        private BigDecimal payablesDue;
        private BigDecimal totalOutgoing;
        /** chequesIncoming - totalOutgoing */
        private BigDecimal net;
        /** Already past its date and still unpaid. */
        private BigDecimal overdueOutgoing;
        /** Outstanding customer balances with no collection date — context only. */
        private BigDecimal undatedReceivable;
        /** Payable GRNs missing their terms, so not in the figures above. */
        private BigDecimal untermed;
        private int untermedCount;
    }

    /** A single dated movement, for the timeline. */
    @Data
    @Builder
    public static class CashFlowEntry {
        private LocalDate date;
        private String business;
        private String direction;   // IN | OUT
        private String source;      // CHEQUE | GRN | PAYABLE
        private String reference;
        private String party;
        private BigDecimal amount;
        private boolean overdue;
    }
}
