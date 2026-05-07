package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignBillRequest {

    @NotNull(message = "Worker ID is required")
    private Long workerId;
}
