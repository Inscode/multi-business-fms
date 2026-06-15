package com.multi.finance.dto.request;

import com.multi.finance.enums.ClarificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TaskClarificationRequest {

    @NotBlank
    private String message;

    @NotNull
    private ClarificationType type;

    private List<String> imageUrls;
}
