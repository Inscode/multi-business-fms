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

@RestController
@RequestMapping("/api/damage-dispatches")
@RequiredArgsConstructor
public class DamageDispatchController {

    private final DamageDispatchService service;

    @GetMapping("/damage-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DamageStockResponse>> getDamageStock(@RequestParam String business) {
        return ResponseEntity.ok(service.getDamageStock(business));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DamageDispatchResponse> create(@RequestBody CreateDamageDispatchRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DamageDispatchResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DamageDispatchResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }
}
