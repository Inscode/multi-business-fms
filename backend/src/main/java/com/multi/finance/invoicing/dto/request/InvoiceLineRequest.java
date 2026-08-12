package com.multi.finance.invoicing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvoiceLineRequest {
    @NotNull Long itemId;

    /** Zero is allowed — a free-only line has no paid quantity. */
    @NotNull @Min(0) Integer qty;

    /** Given free: no value, but still deducted from stock. */
    @Min(0) Integer freeQty;
}
