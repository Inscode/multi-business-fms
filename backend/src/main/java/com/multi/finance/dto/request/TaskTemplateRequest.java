package com.multi.finance.dto.request;

import com.multi.finance.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskTemplateRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private TaskAssignedRole assignedRole;

    @NotNull
    private TaskFrequency frequency;

    @NotNull
    private TaskType taskType;

    private BusinessType businessUnit;

    private String dueTime; // "HH:mm"

    @NotNull
    private UrgencyLevel urgencyLevel;

    private boolean active = true;
}
