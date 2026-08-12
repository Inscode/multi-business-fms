package com.multi.finance.dto.request;

import com.multi.finance.enums.BusinessType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SupplierPayableRequest {
    @NotNull private BusinessType business;
    private String supplierName;
    @NotNull private String description;
    @NotNull private BigDecimal amount;
    /** When the money leaves — the cheque date, or an agreed pay-by date. */
    @NotNull private LocalDate dueDate;
    private String chequeNumber;
    private String bankName;
    private String notes;
}
