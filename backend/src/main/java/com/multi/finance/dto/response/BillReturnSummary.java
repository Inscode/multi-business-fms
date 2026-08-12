package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a bill's returns add up to, for the bill view.
 *
 * <p>Damage and salable are kept apart because they are settled differently: salable
 * goes back on the shelf, damage is claimed off the agent.
 */
@Data
@Builder
public class BillReturnSummary {
    /** What the bill was invoiced for — never reduced by a return. */
    private BigDecimal billTotal;
    private BigDecimal salableTotal;
    private BigDecimal damageTotal;
    /** salableTotal + damageTotal. */
    private BigDecimal returnsTotal;
    /** billTotal - returnsTotal: what the customer actually owes. */
    private BigDecimal payable;
    private BigDecimal amountPaid;
    private BigDecimal balanceRemaining;

    /** Returns still waiting on a confirmation or a review; these block payment. */
    private int openCount;
    private BigDecimal openAmount;

    private List<BillReturnResponse> returns;
}
