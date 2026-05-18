package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AccountantDashboardResponse {
    private long totalBillsToday;
    private long assignedBills;
    private long inShopBills;
    private long receivedBills;
    private long pendingPayments;
    private List<BillSummaryResponse> recentBills;
    private List<BillSummaryResponse> unassignedBills;
}
