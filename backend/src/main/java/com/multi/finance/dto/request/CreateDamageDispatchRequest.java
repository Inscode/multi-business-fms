package com.multi.finance.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateDamageDispatchRequest {
    private String business;
    private LocalDate dispatchDate;
    private String notes;
    private java.math.BigDecimal predictedValue;
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        private Long productId;
        private Integer quantity;
    }
}
