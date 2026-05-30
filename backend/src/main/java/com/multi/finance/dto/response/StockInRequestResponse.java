package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockInRequestResponse {

    private Long id;
    private String referenceNumber;
    private LocalDate stockDate;
    private String notes;
    private String status;
    private String submittedByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectionReason;
    private List<StockInItemDetail> items;

    @Data
    @Builder
    public static class StockInItemDetail {
        private Long productId;
        private String productName;
        private Long quantity;
    }
}
