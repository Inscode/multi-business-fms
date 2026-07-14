package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkerReminderOverviewResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String area;
    private String reminderDate;
    private String period;
    private String note;
    private String status;
    private String createdAt;
}
