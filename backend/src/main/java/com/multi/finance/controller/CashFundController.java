package com.multi.finance.controller;

import com.multi.finance.dto.request.CashFundRequest;
import com.multi.finance.dto.response.CashBalanceResponse;
import com.multi.finance.dto.response.CashFundResponse;
import com.multi.finance.service.impl.CashFundServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-fund")
@RequiredArgsConstructor
public class CashFundController {

    private final CashFundServiceImpl cashFundService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<CashFundResponse> addFund(@RequestBody CashFundRequest req) {
        return ResponseEntity.ok(cashFundService.addFund(req));
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<CashBalanceResponse> getBalance() {
        return ResponseEntity.ok(cashFundService.getBalance());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<CashFundResponse>> getHistory() {
        return ResponseEntity.ok(cashFundService.getHistory());
    }
}