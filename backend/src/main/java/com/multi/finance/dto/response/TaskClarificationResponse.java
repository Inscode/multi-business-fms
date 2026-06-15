package com.multi.finance.dto.response;

import com.multi.finance.enums.ClarificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskClarificationResponse {
    private Long id;
    private Long instanceId;
    private String authorName;
    private String authorRole;
    private String message;
    private ClarificationType type;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
}
