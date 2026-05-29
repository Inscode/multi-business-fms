package com.multi.finance.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ApproveReturnRequest {
    private String approveWith;
    private List<ReceivedItemDto> items;
}