package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SubmitStockInRequest {

    private String referenceNumber;

    @NotNull(message = "Stock date is required")
    private LocalDate stockDate;

    @NotEmpty(message = "At least one item is required")
    private List<StockItemRequest> items;

    private String notes;
}
