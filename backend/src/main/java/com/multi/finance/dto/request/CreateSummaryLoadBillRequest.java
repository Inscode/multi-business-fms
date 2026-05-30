package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateSummaryLoadBillRequest {

    @NotEmpty(message = "At least one system bill must be selected")
    private List<Long> systemBillIds; // IDs of SYSTEM bills to include

    @NotEmpty(message = "At least one item must be entered")
    private List<StockItemRequest> items; // Consolidated items for the load

    @NotNull(message = "Load date is required")
    private LocalDate loadDate;

    private String notes;
}
