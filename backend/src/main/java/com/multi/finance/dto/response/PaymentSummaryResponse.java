package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentSummaryResponse {
    private Long id;
    private String billNumber;
    private String customerName;
    private BigDecimal amount;
    private String paymentType;
    private String enteredByName;
    private LocalDate paymentDate;
    private String status;
    private String collectedByOwnerName;
    private String collectedByWorkerName;
    private String collectorNote;

    /**
     * The photographs, so the dashboard can offer them before a confirmation. Without
     * these the panel had no way to know a photo existed and the view button never
     * appeared, even though the payment carried one.
     */
    private String receiptImageUrl;
    private String confirmImageUrl;
}
