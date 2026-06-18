package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BackorderRequestResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String status;
    private String submittedByName;
    private String reviewedByName;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String notes;
    private String rejectionReason;
    private List<BackorderReqItemResponse> items;
    private BigDecimal totalAmountToAdd;
    private boolean hasInsufficientStock; // true if ANY item has available < qty
}
