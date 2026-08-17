package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer worth being careful with, for the dashboard's ranked list.
 *
 * <p>Built by {@code CustomerHealthService} so this list and the customer's own record
 * cannot disagree. It used to carry its own score — partials plus twice the returned
 * cheques — which had no time in it and no denominator, so a customer with sixty bills
 * and two late ones outranked one with two bills and a bounced cheque.
 */
@Data
@Builder
public class CustomerRiskEntry {
    private Long customerId;
    private String customerName;
    private String area;

    /** GOOD, WATCH or CAREFUL. */
    private String rating;
    /** Why, in words — the list is acted on, so it has to say what it saw. */
    private List<String> reasons;

    private BigDecimal currentOutstanding;
    private BigDecimal overdueAmount;
    private Integer oldestOpenDays;

    /** Days to settle lately, falling back to all time. Null if none ever settled. */
    private Integer avgDaysToSettle;

    private int bouncedChequeCount;
    private LocalDate lastBouncedChequeDate;
    private int partialCount;
}
