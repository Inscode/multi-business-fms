package com.multi.finance.dto.request;

import com.multi.finance.enums.BillSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateStockBillRequest {

    @NotNull(message = "Bill source is required")
    private BillSource billSource; // SYSTEM, DRAFT, or MANUAL

    @NotEmpty(message = "Customer name is required")
    @NotBlank(message = "Customer name cannot be blank")
    private String customerName;

    @NotEmpty(message = "At least one item must be entered")
    private List<StockItemRequest> items;

    @NotNull(message = "Calculated amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal calculatedAmount;

    // For DRAFT/MANUAL: actual amount after discount
    private BigDecimal actualAmount;

    private String area;

    private LocalDate billDate;

    private String notes;
}
