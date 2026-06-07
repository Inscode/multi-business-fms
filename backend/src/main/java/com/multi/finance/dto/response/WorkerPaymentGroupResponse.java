package com.multi.finance.dto.response;

import com.multi.finance.enums.PaymentType;
import com.multi.finance.enums.WorkerPaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WorkerPaymentGroupResponse {
    private Long id;
    private String workerName;
    private PaymentType paymentType;
    private String chequeNumber;
    private String bankName;
    private String branchName;
    private BigDecimal totalAmount;
    private WorkerPaymentStatus status;
    private String workerNote;
    private LocalDateTime createdAt;
    private List<WorkerPaymentEntryResponse> entries;
}
