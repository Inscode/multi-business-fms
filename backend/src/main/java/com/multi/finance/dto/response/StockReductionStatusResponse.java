package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class StockReductionStatusResponse {

    private Long billId;
    private String billNumber;
    private String billSource;     // SYSTEM, DRAFT, MANUAL
    private String customerName;
    private BigDecimal amount;
    private BigDecimal balanceRemaining;
    private LocalDate billDate;
    private String reductionStatus;
    private Long summaryLoadBillId;
    private String enteredByName;

    // Populated for SYSTEM linking bills only
    private Boolean stockReconciled;
    private BigDecimal childrenTotalAmount;
    private BigDecimal savingsAmount;
}
