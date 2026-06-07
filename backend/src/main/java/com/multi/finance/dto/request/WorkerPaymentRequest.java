package com.multi.finance.dto.request;

import com.multi.finance.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerPaymentRequest {
    @NotNull
    private Long billId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotNull
    private PaymentType paymentType;

    private String chequeNumber;
    private String bankName;
    private String branchName;
    private String workerNote;
}
