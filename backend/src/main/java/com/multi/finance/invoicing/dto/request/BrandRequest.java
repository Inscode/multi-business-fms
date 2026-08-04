package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.DiscountType;
import com.multi.finance.invoicing.enums.Principal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BrandRequest {
    @NotBlank String name;
    String brandCode;
    @NotNull CategoryType category;
    // Optional — the brand form doesn't send it; defaults from category server-side
    Principal principal;
    @NotNull DiscountType discountType;
    BigDecimal defaultMarginPct;
    List<SlabRequest> slabs;

    @Data
    public static class SlabRequest {
        BigDecimal minValue;
        BigDecimal maxValue;
        BigDecimal discountPct;
        Integer sortOrder;
    }
}
