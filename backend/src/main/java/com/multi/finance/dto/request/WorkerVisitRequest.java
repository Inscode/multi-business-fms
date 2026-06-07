package com.multi.finance.dto.request;

import com.multi.finance.enums.WorkerVisitStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkerVisitRequest {
    @NotNull
    private WorkerVisitStatus visitStatus;
    private String workerNote; // required for REVISIT_REQUESTED
}
