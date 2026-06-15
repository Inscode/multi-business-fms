package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BillSequenceGapResponse {
    private String billSource;
    private int totalBills;
    private int firstNumber;
    private int lastNumber;
    private int missingCount;
    private List<String> missingNumbers;
}
