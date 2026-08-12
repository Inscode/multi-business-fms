package com.multi.finance.controller;

import com.multi.finance.dto.response.CashFlowResponse;
import com.multi.finance.service.impl.CashFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-flow")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
public class CashFlowController {

    private final CashFlowService service;

    /** Default 70 days — the Rainco credit period. */
    @GetMapping("/forecast")
    public ResponseEntity<CashFlowResponse> forecast(
            @RequestParam(required = false, defaultValue = "70") int days) {
        return ResponseEntity.ok(service.forecast(days));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<CashFlowResponse.CashFlowEntry>> entries(
            @RequestParam(required = false, defaultValue = "70") int days) {
        return ResponseEntity.ok(service.entries(days));
    }
}
