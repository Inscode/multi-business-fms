package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockReductionPendingResponse {

    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String status; // PENDING, APPROVED, REJECTED
    private String submittedByName;
    private String reviewedByName;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String notes;
    private String rejectionReason;
    private List<ItemDto> items;

    @Data
    @Builder
    public static class ItemDto {
        private String productName;
        private Long quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
