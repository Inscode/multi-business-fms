package com.multi.finance.dto.request;

import com.multi.finance.enums.RecipientDesignation;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalaryRecipientRequest {
    private String name;
    private RecipientDesignation designation;
    private BigDecimal monthlySalary;
    private Long linkedWorkerId;
    private Long linkedUserId;
}