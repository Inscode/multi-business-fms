package com.multi.finance.controller;

import com.multi.finance.dto.request.ExpenseRequest;
import com.multi.finance.dto.request.ExpenseAdvanceRequest;
import com.multi.finance.dto.request.SettleExpenseAdvanceRequest;
import com.multi.finance.dto.response.ExpenseResponse;
import com.multi.finance.dto.response.ExpenseAdvanceResponse;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.ExpenseCategory;
import com.multi.finance.enums.ExpenseStatus;
import com.multi.finance.service.impl.ExpenseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseServiceImpl expenseService;
    private final com.multi.finance.service.impl.ExpenseAdvanceService advanceService;

    @GetMapping("/advances")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<ExpenseAdvanceResponse>> advances() {
        return ResponseEntity.ok(advanceService.list());
    }

    @PostMapping("/advances")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<ExpenseAdvanceResponse> issueAdvance(@RequestBody ExpenseAdvanceRequest request) {
        return ResponseEntity.ok(advanceService.issue(request));
    }

    @PostMapping("/advances/{id}/settle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<ExpenseAdvanceResponse> settleAdvance(@PathVariable Long id,
                                                                 @RequestBody SettleExpenseAdvanceRequest request) {
        return ResponseEntity.ok(advanceService.settle(id, request));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<ExpenseResponse> create(@RequestBody ExpenseRequest req) {
        return ResponseEntity.ok(expenseService.create(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<ExpenseResponse>> getAll(
            @RequestParam(required = false) BusinessType business,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(expenseService.getAll(business, category, status, from, to));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ExpenseResponse> review(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.review(id));
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", expenseService.getPendingCount()));
    }
}
