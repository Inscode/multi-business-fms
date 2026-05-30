package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class IndividualStockReductionRequest {

    @NotNull(message = "Bill ID is required")
    private Long billId;

    @NotEmpty(message = "At least one item is required")
    private List<StockItemRequest> items;

    private String notes;
}
