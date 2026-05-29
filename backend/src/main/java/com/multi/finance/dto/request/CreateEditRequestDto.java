package com.multi.finance.dto.request;

import com.multi.finance.enums.EditRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEditRequestDto {

    @NotNull
    private EditRequestType type;

    @NotNull
    private Long targetId;

    @NotNull
    private String targetRef;

    @NotNull
    private String requestedChanges;

    private String reason;
}