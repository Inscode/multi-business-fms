package com.multi.finance.dto.response;

import com.multi.finance.enums.CollectionNoteStatus;
import com.multi.finance.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CollectionNoteResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String area;
    private BigDecimal billBalance;
    private BigDecimal amount;
    private PaymentType paymentType;
    private CollectionNoteStatus status;
    private String collectedByName;
    private LocalDateTime collectedAt;
    private String notes;
}