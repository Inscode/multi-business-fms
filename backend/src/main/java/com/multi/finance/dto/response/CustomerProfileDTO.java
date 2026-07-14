package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CustomerProfileDTO {

    private CustomerInfo customer;
    private CustomerSummary summary;
    private List<OutstandingBillDTO> unpaidBills;
    private List<PaymentHistoryItem> paymentHistory;
    private List<ReminderItem> reminders;

    @Data
    @Builder
    public static class CustomerInfo {
        private String name;
        private String phone;
        private String area;
        private String tier;
        private String shopType;
        private Boolean active;
    }

    @Data
    @Builder
    public static class CustomerSummary {
        private int unpaidBillCount;
        private BigDecimal totalOutstanding;
        private long oldestUnpaidDays;
        private LocalDate oldestBillDate;
    }

    @Data
    @Builder
    public static class PaymentHistoryItem {
        private Long paymentId;
        private String billNumber;
        private BigDecimal amount;
        private String paymentType;
        private String status;
        private LocalDate paymentDate;
        private String chequeNumber;
        private String bankName;
        private String returnReason;
    }

    @Data
    @Builder
    public static class ReminderItem {
        private LocalDate reminderDate;
        private String note;
        private String createdBy;
        private String billNumber;
    }
}
