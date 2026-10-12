package com.multi.finance.dto.request;

import com.multi.finance.enums.ExpenseCategory;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SettleExpenseAdvanceRequest {
    private BigDecimal returnedAmount;
    private List<Item> items;

    @Data
    public static class Item {
        private ExpenseCategory category;
        private BigDecimal amount;
        private String description;
        private String receiptImageUrl;
        private Long busFareId;
    }
}
