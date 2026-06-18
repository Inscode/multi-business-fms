package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CollectionHealthResponse {
    private List<DsoTrendEntry> dsoTrend;
    private List<CollectorPerformanceEntry> collectorLeaderboard;
    private List<UpcomingChequeEntry> upcomingCheques;
    private List<StaleChequeEntry> staleCheques;
    private List<CustomerRiskEntry> riskyCustomers;
    private List<PaymentMixEntry> paymentMix;
}
