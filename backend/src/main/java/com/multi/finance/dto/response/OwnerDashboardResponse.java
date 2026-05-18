package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OwnerDashboardResponse {
    private long unassignedBills;
    private long inFieldBills;
    private long inShopBills;
    private long awaitingConfirmation;
    private long fullyPaidToday;
    private BigDecimal totalOutstanding;
    private List<PaymentSummaryResponse> pendingPayments;
    private List<BillSummaryResponse> unassignedBillList;
}
