package com.multi.finance.dto.response;

import com.multi.finance.enums.WorkerFinanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WorkerTabPurchaseResponse {
    private Long id;
    private Long recipientId;
    private String recipientName;
    private LocalDate billDate;
    private String month;
    private BigDecimal totalAmount;
    private WorkerFinanceStatus status;
    private String notes;
    private List<ItemResponse> items;
    private String enteredByName;
    private String ownerReviewedByName;
    private LocalDateTime ownerReviewedAt;
    private String rejectionReason;
    private String adminConfirmedByName;
    private LocalDateTime adminConfirmedAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class ItemResponse {
        private Long id;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
