package com.multi.finance.dto.response;

import com.multi.finance.enums.PaymentType;
import com.multi.finance.enums.WorkerPaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WorkerPaymentEntryResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private Long groupId;
    private String workerName;
    private BigDecimal amount;
    private PaymentType paymentType;
    private String chequeNumber;
    private String bankName;
    private String branchName;
    private WorkerPaymentStatus status;
    private String workerNote;
    private LocalDateTime enteredAt;
    private LocalDateTime confirmedAt;
    private String confirmedByName;
    private String rejectedReason;
}
