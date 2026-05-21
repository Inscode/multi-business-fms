package com.multi.finance.dto.request;

import com.multi.finance.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "Role is required")
    private UserRole role;

    private String password;

    private Boolean active;
}