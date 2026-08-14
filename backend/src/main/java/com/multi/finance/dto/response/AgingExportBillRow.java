package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One outstanding bill on the printable aging report. */
@Data
@Builder
public class AgingExportBillRow {
    private String billNumber;
    private LocalDate billDate;
    private long ageDays;
    private String customerName;
    private String area;
    private String billType;        // CASH | CREDIT
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private String bucket;          // credit: 0-30/31-60/61-90/91+ · cash: Today/1-7/8-14/15+
    private String workerName;
    private LocalDate lastPaymentDate;
}
