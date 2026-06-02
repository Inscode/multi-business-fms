package com.multi.finance.controller;

import com.multi.finance.dto.request.WorkerAdvanceBonusRequest;
import com.multi.finance.dto.response.WorkerAdvanceBonusResponse;
import com.multi.finance.service.impl.WorkerAdvanceBonusServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worker-finance/advance-bonus")
@RequiredArgsConstructor
public class WorkerAdvanceBonusController {

    private final WorkerAdvanceBonusServiceImpl service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<WorkerAdvanceBonusResponse> create(@RequestBody WorkerAdvanceBonusRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/pending-owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getPendingOwner() {
        return ResponseEntity.ok(service.getPendingOwner());
    }

    @GetMapping("/pending-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getPendingAdmin() {
        return ResponseEntity.ok(service.getPendingAdmin());
    }

    @GetMapping("/recipient/{recipientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getForRecipient(@PathVariable Long recipientId) {
        return ResponseEntity.ok(service.getForRecipient(recipientId));
    }

    @GetMapping("/recipient/{recipientId}/outstanding-advances")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getOutstandingAdvances(@PathVariable Long recipientId) {
        return ResponseEntity.ok(service.getOutstandingAdvances(recipientId));
    }

    @PatchMapping("/{id}/owner-approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<WorkerAdvanceBonusResponse> ownerApprove(@PathVariable Long id) {
        return ResponseEntity.ok(service.ownerApprove(id));
    }

    @PatchMapping("/{id}/owner-reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<WorkerAdvanceBonusResponse> ownerReject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(service.ownerReject(id, reason));
    }

    @PatchMapping("/{id}/admin-confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkerAdvanceBonusResponse> adminConfirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.adminConfirm(id));
    }

    @PatchMapping("/{id}/recover")
    @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<WorkerAdvanceBonusResponse> recoverAdvance(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String recoveryMonth = body.containsKey("recoveryMonth") ? body.get("recoveryMonth").toString() : null;
        return ResponseEntity.ok(service.recoverAdvance(id, amount, recoveryMonth));
    }

    @GetMapping("/by-date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerAdvanceBonusResponse>> getByDateRange(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(service.getPaidByDateRange(LocalDate.parse(from), LocalDate.parse(to)));
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
