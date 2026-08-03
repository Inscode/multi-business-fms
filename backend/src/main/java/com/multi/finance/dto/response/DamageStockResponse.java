package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DamageStockResponse {
    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private Long damageQty;
}
