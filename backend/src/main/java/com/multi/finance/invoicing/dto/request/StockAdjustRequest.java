package com.multi.finance.invoicing.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockAdjustRequest {
    @NotNull Long itemId;
    @NotNull Integer delta;   // positive = add, negative = remove
    String notes;
}
