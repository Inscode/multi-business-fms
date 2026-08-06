package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillAuditSessionResponse {
    private Long id;
    private LocalDate periodMonth;
    private String businessScope;
    private String areaScope;
    private String openedByName;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    // Live counts across the bills in scope
    private int totalInScope;
    private int inHand;
    private int paidNotEntered;
    private int missing;
    private int unchecked;
}
