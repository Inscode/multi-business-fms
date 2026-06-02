package com.multi.finance.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class WorkerTabPurchaseRequest {
    private Long recipientId;
    private LocalDate billDate;
    private String notes;
    private List<WorkerTabPurchaseItemRequest> items;

    @Data
    public static class WorkerTabPurchaseItemRequest {
        private String description;
        private java.math.BigDecimal quantity;
        private java.math.BigDecimal unitPrice;
    }
}
