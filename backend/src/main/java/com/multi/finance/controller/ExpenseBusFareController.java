package com.multi.finance.controller;

import com.multi.finance.dto.request.ExpenseBusFareRequest;
import com.multi.finance.entity.ExpenseBusFare;
import com.multi.finance.service.impl.ExpenseBusFareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/expenses/bus-fares") @RequiredArgsConstructor
public class ExpenseBusFareController {
    private final ExpenseBusFareService service;

    @GetMapping @PreAuthorize("hasAnyRole('ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public List<ExpenseBusFare> list() { return service.active(); }

    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ExpenseBusFare create(@RequestBody ExpenseBusFareRequest request) { return service.create(request); }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ExpenseBusFare update(@PathVariable Long id, @RequestBody ExpenseBusFareRequest request) { return service.update(id, request); }

    @PatchMapping("/{id}/active") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExpenseBusFare> active(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(service.active(id, Boolean.TRUE.equals(body.get("active"))));
    }
}
