package com.multi.finance.controller;

import com.multi.finance.dto.request.WorkerTabPurchaseRequest;
import com.multi.finance.dto.response.WorkerSalaryDeductionResponse;
import com.multi.finance.dto.response.WorkerTabPurchaseResponse;
import com.multi.finance.service.impl.WorkerTabPurchaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worker-finance/tab-purchases")
@RequiredArgsConstructor
public class WorkerTabPurchaseController {

    private final WorkerTabPurchaseServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<WorkerTabPurchaseResponse> create(@RequestBody WorkerTabPurchaseRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/pending-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getPendingOwner() {
        return ResponseEntity.ok(service.getPendingOwner());
    }

    @GetMapping("/pending-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getPendingAdmin() {
        return ResponseEntity.ok(service.getPendingAdmin());
    }

    @GetMapping("/recipient/{recipientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getForRecipient(@PathVariable Long recipientId) {
        return ResponseEntity.ok(service.getForRecipient(recipientId));
    }

    @GetMapping("/paid")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getPaidForMonth(
            @RequestParam Long recipientId,
            @RequestParam String month) {
        return ResponseEntity.ok(service.getPaidForMonth(recipientId, month));
    }

    @PatchMapping("/{id}/owner-approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<WorkerTabPurchaseResponse> ownerApprove(@PathVariable Long id) {
        return ResponseEntity.ok(service.ownerApprove(id));
    }

    @PatchMapping("/{id}/owner-reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<WorkerTabPurchaseResponse> ownerReject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(service.ownerReject(id, reason));
    }

    @PatchMapping("/{id}/admin-confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkerTabPurchaseResponse> adminConfirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.adminConfirm(id));
    }

    @GetMapping("/by-date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerTabPurchaseResponse>> getByDateRange(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(service.getPaidByDateRange(LocalDate.parse(from), LocalDate.parse(to)));
    }

    @GetMapping("/salary-deductions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerSalaryDeductionResponse>> getSalaryDeductions(
            @RequestParam Long recipientId,
            @RequestParam String month) {
        return ResponseEntity.ok(service.getSalaryDeductions(recipientId, month));
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, Long>> getCounts() {
        return ResponseEntity.ok(Map.of(
                "pendingOwner", service.getPendingOwnerCount(),
                "pendingAdmin", service.getPendingAdminCount()
        ));
    }
}
