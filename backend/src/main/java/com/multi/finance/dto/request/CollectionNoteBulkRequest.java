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
public class CollectionNoteBulkRequest {

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    private String notes;

    // Cheque fields — all optional
    private String chequeNumber;
    private String bankName;
    private String branchName;
    private LocalDate chequeDate;

    // Bank transfer — optional
    private String referenceNumber;

    @NotNull
    @NotEmpty(message = "At least one bill entry is required")
    @Valid
    private List<BillEntry> bills;

    @Data
    public static class BillEntry {
        @NotNull(message = "Bill ID is required")
        private Long billId;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        private BigDecimal amount;
    }

    /** Photo of the bill(s). Optional — the combined collection screen is admin-facing. */
    private String receiptImageUrl;
}
