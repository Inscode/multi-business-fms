package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class BackorderItemResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private Long productId;
    private String productName;
    private Long quantity;
    private BigDecimal predictedUnitPrice;
    private BigDecimal lineTotal;
    private LocalDateTime createdAt;
}
