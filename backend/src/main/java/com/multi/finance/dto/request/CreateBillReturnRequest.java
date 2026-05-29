package com.multi.finance.dto.request;

import com.multi.finance.enums.ReturnType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateBillReturnRequest {
    private ReturnType returnType;
    private List<BillReturnItemRequest> items;
    private BigDecimal discountPercentage;
    private BigDecimal discountFixed;
    private BigDecimal predictedValue;
    private Long responsibleWorkerId;
    private String notes;
}