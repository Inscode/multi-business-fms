package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DamageDispatchResponse {
    private Long id;
    private String business;
    private LocalDate dispatchDate;
    private BigDecimal totalValue;
    private BigDecimal predictedValue;
    private String notes;
    private String enteredByName;
    private String status;
    private String rejectionReason;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private List<ItemResponse> items;

    @Data
    @Builder
    public static class ItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal lineTotal;
    }
}
