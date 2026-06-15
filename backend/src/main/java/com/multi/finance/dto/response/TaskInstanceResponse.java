package com.multi.finance.dto.response;

import com.multi.finance.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskInstanceResponse {
    private Long id;
    private TaskTemplateResponse template;
    private LocalDate date;
    private TaskStatus status;
    private LocalDateTime completedAt;
    private String movedReason;
    private LocalDate movedToDate;
    private List<String> attachmentUrls;
}
