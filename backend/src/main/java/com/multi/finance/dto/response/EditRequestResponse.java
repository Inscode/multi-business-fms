package com.multi.finance.dto.response;

import com.multi.finance.enums.EditRequestStatus;
import com.multi.finance.enums.EditRequestType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EditRequestResponse {
    private Long id;
    private EditRequestType type;
    private Long targetId;
    private String targetRef;
    private String requestedChanges;
    private String reason;
    private String requestedByName;
    private LocalDateTime requestedAt;
    private EditRequestStatus status;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
}