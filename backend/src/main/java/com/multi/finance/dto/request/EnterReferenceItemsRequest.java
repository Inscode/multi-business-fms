package com.multi.finance.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class EnterReferenceItemsRequest {
    private List<StockItemRequest> items;
}
