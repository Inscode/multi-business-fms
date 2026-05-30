package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductStockBalanceResponse {
    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private Boolean active;
    private Long availableQty;
    private Long damageQty;
}
