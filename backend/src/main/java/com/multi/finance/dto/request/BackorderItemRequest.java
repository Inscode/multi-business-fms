package com.multi.finance.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BackorderItemRequest {
    @NotNull
    private Long productId;
    @NotNull
    @Min(1)
    private Long quantity;
    private BigDecimal amountToAdd; // optional, default 0
}
