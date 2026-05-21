package com.multi.finance.dto.request;

import com.multi.finance.enums.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class BulkPaymentRequest {

    @NotEmpty(message = "At least one bill is required")
    @Valid
    private List<BillPaymentItem> bills;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    private String chequeNumber;
    private String bankName;
    private String branchName;
    private String referenceNumber;
    private LocalDate chequeDate;
    private LocalDate paymentDate;
    private String notes;

    @Data
    public static class BillPaymentItem {

        @NotNull(message = "Bill ID is required")
        private Long billId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        private BigDecimal amount;
    }
}
