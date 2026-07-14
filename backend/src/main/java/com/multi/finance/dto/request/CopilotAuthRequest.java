package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CopilotAuthRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
