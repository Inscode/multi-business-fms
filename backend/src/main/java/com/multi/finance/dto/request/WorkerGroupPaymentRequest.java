package com.multi.finance.dto.request;

import com.multi.finance.enums.PaymentType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkerGroupPaymentRequest {

    @NotNull
    private PaymentType paymentType;       // usually CHEQUE for combined

    private String chequeNumber;
    private String bankName;
    private String branchName;
    private String workerNote;

    @NotEmpty
    private List<BillAmount> bills;

    @Data
    public static class BillAmount {
        @NotNull
        private Long billId;
        @NotNull
        @jakarta.validation.constraints.DecimalMin("0.01")
        private BigDecimal amount;
    }
}
