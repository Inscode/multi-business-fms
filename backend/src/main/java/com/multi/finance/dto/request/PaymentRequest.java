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

    /** Photograph of the bill. Required when an accountant enters the payment. */
    private String receiptImageUrl;

    private Long collectionNoteId;

    private Long collectedByWorkerId;

    private String collectorNote;
}