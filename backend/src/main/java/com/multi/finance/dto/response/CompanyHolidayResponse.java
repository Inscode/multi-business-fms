package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompanyHolidayResponse {
    private Long id;
    private String date;
    private String name;
    private String holidayType;
    private boolean appliesToAll;
    private List<Long> workerIds;
    private List<String> workerNames;
    private String createdAt;
}
