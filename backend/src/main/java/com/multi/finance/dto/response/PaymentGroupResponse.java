package com.multi.finance.dto.response;

import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PaymentGroupResponse {

    private Long id;
    private PaymentType paymentType;
    private String chequeNumber;
    private String bankName;
    private String branchName;
    private String referenceNumber;
    private LocalDate chequeDate;
    private LocalDate paymentDate;
    private BigDecimal totalAmount;
    private String notes;
    private PaymentStatus status;
    private String enteredByName;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private String returnReason;
    private LocalDateTime createdAt;
    private List<PaymentResponse> payments;
}
