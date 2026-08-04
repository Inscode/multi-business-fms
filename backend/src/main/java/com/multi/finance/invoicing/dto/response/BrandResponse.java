package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.DiscountType;
import com.multi.finance.invoicing.enums.Principal;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BrandResponse {
    private Long id;
    private String name;
    private String brandCode;
    private CategoryType category;
    private Principal principal;
    private DiscountType discountType;
    private BigDecimal defaultMarginPct;
    private boolean active;
    private List<SlabResponse> slabs;

    @Data
    public static class SlabResponse {
        private Long id;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private BigDecimal discountPct;
        private Integer sortOrder;
    }
}
