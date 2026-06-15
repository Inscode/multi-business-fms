package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OwnerDashboardResponse {
    private long assignedBills;        // bills currently out to collection (status=ASSIGNED)
    private long inFieldBills;
    private long inShopBills;
    private long awaitingConfirmation;
    private long pendingBills;         // active bills (CREATED + ASSIGNED + SHOP_* + STORE_RECEIVED)
    private BigDecimal totalOutstanding;

    // Today's bills entered per business
    private long raincoTodayBills;
    private long stationeryTodayBills;
    private long plasticTodayBills;

    // Today's payments entered per business
    private long raincoTodayPayments;
    private long stationeryTodayPayments;
    private long plasticTodayPayments;

    private BigDecimal cashPending;        // total outstanding cash bill balance
    private BigDecimal cashSerious;        // cash bills 15+ days outstanding (serious issue)

    private List<PaymentSummaryResponse> pendingPayments;
    private List<BillSummaryResponse> assignedBillList;  // bills out to collection
}
