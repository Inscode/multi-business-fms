package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {
    private Long id;
    private String name;
    private String phone;
    private String area;
    private String tier;
    private String shopType;
    private Boolean active;
    private LocalDateTime createdAt;
}
