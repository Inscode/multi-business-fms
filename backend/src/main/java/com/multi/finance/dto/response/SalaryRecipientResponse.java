package com.multi.finance.dto.response;

import com.multi.finance.enums.RecipientDesignation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalaryRecipientResponse {
    private Long id;
    private String name;
    private RecipientDesignation designation;
    private BigDecimal monthlySalary;
    private Boolean active;
}