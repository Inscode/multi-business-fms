package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerRequest {

    @NotBlank(message = "Customer name is required")
    private String name;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Area is required")
    private String area;

    private String tier;      // e.g. GOLD, SILVER, BRONZE

    private String shopType;  // e.g. WHOLESALE, RETAIL
}
