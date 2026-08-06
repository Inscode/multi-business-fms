package com.multi.finance.dto.response;

import com.multi.finance.enums.BillAuditMarkType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One pending bill inside a sweep, with its mark (null while unchecked). */
@Data
@Builder
public class BillAuditRowResponse {
    private Long billId;
    private String billNumber;
    private LocalDate billDate;
    private String customerName;
    private String area;
    private String business;
    private BigDecimal totalAmount;
    private BigDecimal balanceRemaining;
    private String status;
    private String workerName;

    private BillAuditMarkType markType;
    private String note;
    private String markedByName;
    private LocalDateTime markedAt;
}
