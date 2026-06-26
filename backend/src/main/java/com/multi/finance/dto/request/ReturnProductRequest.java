package com.multi.finance.dto.request;

import com.multi.finance.enums.BusinessType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReturnProductRequest {
    private BusinessType business;
    private String name;
    private BigDecimal unitPrice;
    private Boolean isStockProduct;
    private Boolean isReturnProduct;
}