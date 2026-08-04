package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.CategoryType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemResponse {
    private Long id;
    private String itemCode;
    private String description;
    private CategoryType category;
    private Long brandId;
    private String brandName;
    private BigDecimal mrp;
    private BigDecimal marginPct;
    private BigDecimal wsp;         // computed: mrp * (1 - marginPct/100)
    private BigDecimal wholesalePrice;
    private boolean active;
    private Integer stockQty;
}
