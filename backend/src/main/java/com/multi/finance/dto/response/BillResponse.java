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
    private Long customerId;
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
    private Boolean willBeLinked;
    private Boolean stockCleared;
    private Boolean collectionOnly;

    /** How it reached the customer, and the round it travelled on. */
    /** Why it was voided. Its number is reusable, so this is what tells two apart. */
    /** Collected on another bill — the hand-written one for the same sale. */
    private Long settledOnBillId;
    private String settledOnBillNumber;
    private String settledOnStatus;
    private String settledOnNote;
    private String settledOnBy;

    private String cancelReason;
    private String cancelledBy;
    private java.time.LocalDateTime cancelledAt;

    /** Kept off the aging report, and why. The balance is still owed. */
    private Boolean excludedFromAging;
    private String agingExclusionReason;
    private String agingExcludedBy;

    private String deliveryMode;
    private Long deliveryRunId;
    private String deliveryRunArea;
    private java.time.LocalDate deliveryRunDate;
}
