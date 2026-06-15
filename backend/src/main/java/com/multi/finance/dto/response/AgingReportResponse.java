package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AgingReportResponse {
    private BigDecimal grandTotalOutstanding;
    private BigDecimal grandOverdue;        // credit 45+ days
    private BigDecimal grandCashPending;    // all outstanding cash bills
    private BigDecimal grandCashSerious;    // cash bills 15+ days
    private int totalCustomers;
    private int totalBills;
    private List<AgingCustomerEntry> topCustomers;   // top 20 by outstanding
    private List<AgingCustomerEntry> allCustomers;   // all, for collection priority tab
    private List<AgingAreaSummary>   byArea;
}
