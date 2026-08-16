package com.multi.finance.dto.response;

import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long billId;
    private Long groupId;
    private String customerName;
    private BusinessType business;
    private BigDecimal billTotal;
    private BigDecimal amountPaid;
    private BigDecimal balanceRemaining;
    private Boolean fullyPaid;
    private BigDecimal paymentAmount;
    private String area;
    private PaymentType paymentType;
    private PaymentStatus status;
    private String chequeNumber;
    private LocalDate chequeDate;
    private String bankName;
    private String branchName;
    private String referenceNumber;
    private String returnReason;
    private String billNumber;
    private LocalDate billDate;
    private Boolean isPartial;
    private String enteredByName;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private LocalDate paymentDate;
    private String notes;
    private LocalDateTime createdAt;

    private Long collectionNoteId;
    private String collectedByOwnerName;
    private LocalDateTime collectedByOwnerAt;

    private Long collectedByWorkerId;
    private String collectedByWorkerName;
    private String collectorNote;

    /** Photo of the bill taken when the payment was entered. */
    private String receiptImageUrl;
    private java.time.LocalDateTime receiptUploadedAt;

    /** The admin's own photo, attached on confirmation. Optional. */
    private String confirmImageUrl;
    private java.time.LocalDateTime confirmUploadedAt;
}
