package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemRequest {
    @NotBlank String itemCode;
    @NotBlank String description;
    @NotNull CategoryType category;
    @NotNull Long brandId;
    BigDecimal mrp;
    BigDecimal marginPct;
    BigDecimal wholesalePrice;
}
