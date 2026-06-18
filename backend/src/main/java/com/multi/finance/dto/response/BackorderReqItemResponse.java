package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BackorderReqItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private BigDecimal amountToAdd;
    private Long availableQty; // current available stock (for "ready to give" indicator)
}
