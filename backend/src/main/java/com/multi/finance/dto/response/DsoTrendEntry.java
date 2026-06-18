package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

/** Average days-to-collect for credit bills issued in this month and now fully settled — a DSO proxy. */
@Data
@Builder
public class DsoTrendEntry {
    private String month;            // "YYYY-MM"
    private double avgCollectionDays;
    private int settledBillCount;
}
