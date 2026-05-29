package com.multi.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillReturnItemRequest {
    private Long productId;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantityRequested;
    private Integer quantityReturned;
}