package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class BillChecklistVerifyRowResponse {
    private String itemName;
    private int carryForward;
    private LocalDate carryFromDate;   // oldest date contributing to carry forward
    private int todayNotes;
    private int total;
    private int markedEntered;
    private int balance;
}
