package com.multi.finance.dto.response;


import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillResponse {
    private Long id;
    private String billNumber;
    private BusinessType business;
    private String division;
    private BillType billType;
    private BillSource billSource;
    private String customerName;
    private String area;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balanceRemaining;
    private BillStatus status;
    private boolean fullyPaid;
    private Long workerId;
    private String workerName;
    private String enteredByName;
    private String receivedByName;
    private LocalDateTime receivedAt;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private LocalDate billDate;
    private String notes;
    private LocalDateTime createdAt;
}
