package com.multi.finance.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    private Boolean active;
    private LocalDateTime createdAt;
}
