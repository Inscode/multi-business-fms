package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillChecklistDashboardResponse {
    private int totalNotedToday;   // sum of all counts added today
    private int totalEnteredToday; // sum of markedEntered for today's closing
    private int pendingBalance;    // totalNotedToday - totalEnteredToday (+ any carry forward)
    private int itemsWithBalance;  // count of distinct items that have balance > 0
}
