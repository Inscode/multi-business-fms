package com.multi.finance.dto.response;

import com.multi.finance.enums.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TaskTemplateResponse {
    private Long id;
    private String title;
    private String description;
    private TaskAssignedRole assignedRole;
    private TaskFrequency frequency;
    private TaskType taskType;
    private BusinessType businessUnit;
    private String dueTime;
    private UrgencyLevel urgencyLevel;
    private boolean active;
    private LocalDateTime createdAt;
}
