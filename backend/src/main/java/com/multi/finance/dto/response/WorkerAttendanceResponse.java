package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkerAttendanceResponse {
    private Long id;
    private Long workerId;
    private String workerName;
    private String date;
    private String attendanceType;
    private Object hoursWorked; // kept for API compatibility, always null (hours come from time entries)
    private String notes;
}
