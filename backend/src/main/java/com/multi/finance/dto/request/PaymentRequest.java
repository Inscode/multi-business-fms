package com.multi.finance.dto.request;

import com.multi.finance.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Amount must be greater than zero"
    )
    private BigDecimal amount;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    private String referenceNumber;

    private String bankName;

    private String branchName;

    private String chequeNumber;

    private LocalDate chequeDate;

    private LocalDate paymentDate;

    private String notes;

    // Optional — links this payment to an owner collection note
    private Long collectionNoteId;
}