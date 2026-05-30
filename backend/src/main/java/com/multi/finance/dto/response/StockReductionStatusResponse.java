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
    private LocalDate billDate;
    private String reductionStatus; // NOT_REDUCED, SUMMARY_PENDING, SUMMARY_APPROVED, INDIVIDUALLY_REDUCED
    private Long summaryLoadBillId; // populated if in a summary load
    private String enteredByName;
}
