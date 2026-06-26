package com.multi.finance.controller;

import com.multi.finance.dto.request.ReturnProductRequest;
import com.multi.finance.dto.response.ReturnProductResponse;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.ReturnProductServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/return-products")
@RequiredArgsConstructor
public class ReturnProductController {

    private final ReturnProductServiceImpl returnProductService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<ReturnProductResponse>> getAll() {
        return ResponseEntity.ok(returnProductService.getAll());
    }

    @GetMapping("/by-business/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<ReturnProductResponse>> getByBusiness(@PathVariable BusinessType business) {
        return ResponseEntity.ok(returnProductService.getByBusiness(business));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnProductResponse> create(@RequestBody ReturnProductRequest req) {
        return ResponseEntity.ok(returnProductService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReturnProductResponse> update(@PathVariable Long id, @RequestBody ReturnProductRequest req) {
        return ResponseEntity.ok(returnProductService.update(id, req));
    }

    @PatchMapping("/{id}/set-damage-qty")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setDamageQty(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        returnProductService.setDamageQty(id, body.get("qty"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        returnProductService.setReturnProduct(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        returnProductService.setReturnProduct(id, true);
        return ResponseEntity.noContent().build();
    }
}