package com.multi.finance.dto.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class BillChecklistClosingRequest {
    private LocalDate closingDate;
    private List<ClosingItem> items;

    @Data
    public static class ClosingItem {
        private String itemName;
        private int markedEntered;
    }
}
