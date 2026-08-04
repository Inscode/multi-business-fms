package com.multi.finance.invoicing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvoiceLineRequest {
    @NotNull Long itemId;
    @NotNull @Min(1) Integer qty;
}
