package com.multi.finance.dto.request;

import com.multi.finance.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateTaskStatusRequest {

    @NotNull
    private TaskStatus status;

    private String movedReason;

    private LocalDate movedToDate;
}
