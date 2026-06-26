package com.multi.finance.dto.response;

import com.multi.finance.enums.BusinessType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReturnProductResponse {
    private Long id;
    private BusinessType business;
    private String name;
    private BigDecimal unitPrice;
    private Boolean active;
    private Boolean isStockProduct;
    private Boolean isReturnProduct;
    private LocalDateTime createdAt;
}