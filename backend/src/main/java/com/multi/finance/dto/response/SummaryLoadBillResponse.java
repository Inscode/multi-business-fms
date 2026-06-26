package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SummaryLoadBillResponse {

    private Long id;
    private List<Long> systemBillIds;
    private Integer numberOfBills;
    private Long totalQuantity;
    private LocalDate loadDate;
    private String notes;
    private String status; // PENDING, APPROVED, REJECTED
    private String createdByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private List<ItemDto> items;

    @Data
    @Builder
    public static class ItemDto {
        private Long id;
        private String productName;
        private Long quantity;
        private BigDecimal unitPrice;
    }
}
