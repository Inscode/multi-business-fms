package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BulkAssignBillRequest {

    @NotEmpty(message = "At least one bill must be selected")
    private List<Long> billIds;

    @NotNull(message = "Worker ID is required")
    private Long workerId;
}
