package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShopDashboardResponse {
    private long shopReceivedBills;
    private long shopWorkerAssignedBills;
    private long pendingPayments;
    private List<BillResponse> bills;
}
