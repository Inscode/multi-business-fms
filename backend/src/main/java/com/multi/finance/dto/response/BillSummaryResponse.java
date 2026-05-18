package com.multi.finance.dto.response;

import com.multi.finance.enums.BillStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class BillSummaryResponse {
    private Long id;
    private String billNumber;
    private String customerName;
    private BigDecimal totalAmount;
    private String workerName;
    private BillStatus status;
}
