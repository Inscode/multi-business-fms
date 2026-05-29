package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BillReturnItemResponse {
    private Long id;
    private Long productId;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantityRequested;
    private Integer quantityReturned;
    private BigDecimal lineTotal;
}