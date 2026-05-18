package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private DashboardStatsResponse stats;
    private List<BillSummaryResponse> recentBills;
}
