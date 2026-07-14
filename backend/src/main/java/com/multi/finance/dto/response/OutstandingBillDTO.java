package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class OutstandingBillDTO {
    private Long billId;
    private String billNumber;
    private String customerName;
    private String phone;
    private String area;
    private String tier;
    private String shopType;
    private String business;
    private String billType;
    private LocalDate billDate;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balanceRemaining;
    private String status;
    private long daysSinceBill;
    private boolean isOverdue;
    private String overdueReason;
    private long daysOverdue;
}
