package com.multi.finance.controller;

import com.multi.finance.dto.request.CreateDamageDispatchRequest;
import com.multi.finance.dto.response.DamageDispatchResponse;
import com.multi.finance.dto.response.DamageStockResponse;
import com.multi.finance.service.impl.DamageDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sending damaged goods back to the company. Accountants prepare a dispatch;
 * only an admin can approve it, and damage stock is deducted at that point.
 */
@RestController
@RequestMapping("/api/damage-dispatches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
public class DamageDispatchController {

    private final DamageDispatchService service;

    @GetMapping("/damage-stock")
    public ResponseEntity<List<DamageStockResponse>> getDamageStock(@RequestParam String business) {
        return ResponseEntity.ok(service.getDamageStock(business));
    }

    @PostMapping
    public ResponseEntity<DamageDispatchResponse> create(@RequestBody CreateDamageDispatchRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    public ResponseEntity<List<DamageDispatchResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DamageDispatchResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /** Approval is the point damage stock actually leaves. */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DamageDispatchResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DamageDispatchResponse> reject(@PathVariable Long id,
                                                         @RequestParam String reason) {
        return ResponseEntity.ok(service.reject(id, reason));
    }
}
