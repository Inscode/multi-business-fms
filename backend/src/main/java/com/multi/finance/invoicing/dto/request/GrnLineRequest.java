package com.multi.finance.invoicing.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GrnLineRequest {
    @NotNull Long itemId;
    @NotNull @Min(1) Integer qty;
    // No unit cost — it always comes from the catalog, so a typo can't price a note
}
