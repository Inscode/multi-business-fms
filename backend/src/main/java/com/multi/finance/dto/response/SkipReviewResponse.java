package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SkipReviewResponse {
    private Long id;
    private String business;
    private String skippedBillNumber;
    private String relatedBillNumber;
    private String customerName;
    private String submittedByName;
    private LocalDateTime submittedAt;
}
