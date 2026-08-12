package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Printable / downloadable aging report. Cash and credit are kept apart because
 * they age on different scales — credit in 30-day bands, cash in days.
 */
@Data
@Builder
public class AgingExportResponse {
    private String business;
    private String area;            // null = all areas; several are comma-joined

    /** Bills an admin has kept off this report, and what they come to. */
    private Integer excludedCount;
    private java.math.BigDecimal excludedAmount;
    private String billType;        // null = both cash and credit
    private LocalDate generatedOn;

    private List<AgingCustomerEntry> creditCustomers;
    private List<AgingCustomerEntry> cashCustomers;
    private List<AgingExportBillRow> bills;

    private BigDecimal totalCredit;
    private BigDecimal totalCash;
    private BigDecimal totalOutstanding;
    private int customerCount;
    private int billCount;
}
