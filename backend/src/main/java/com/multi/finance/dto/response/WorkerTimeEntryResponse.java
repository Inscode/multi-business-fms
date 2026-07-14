package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkerTimeEntryResponse {
    private Long id;
    private Long workerId;
    private String workerName;
    private String date;
    private String clockIn;
    private String clockOut;
    private Long workedMinutes;
    private String notes;
}
