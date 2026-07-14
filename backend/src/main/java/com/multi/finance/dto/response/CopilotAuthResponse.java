package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CopilotAuthResponse {
    private String token;
    private String username;
    private String role;
    private long expiresIn;   // seconds remaining
    private String expiresAt; // ISO-8601
}
