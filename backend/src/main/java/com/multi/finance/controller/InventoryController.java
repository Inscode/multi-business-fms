package com.multi.finance.controller;

import com.multi.finance.dto.request.ReturnProductRequest;
import com.multi.finance.dto.request.SubmitStockInRequest;
import com.multi.finance.dto.response.ProductStockBalanceResponse;
import com.multi.finance.dto.response.ReturnProductResponse;
import com.multi.finance.dto.response.StockInRequestResponse;
import com.multi.finance.service.impl.InventoryServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryServiceImpl inventoryService;

    // ── Stock balances ────────────────────────────────────────────

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<ProductStockBalanceResponse>> getBalances() {
        return ResponseEntity.ok(inventoryService.getRaincoStockBalances());
    }

    // ── Products ──────────────────────────────────────────────────

    /** ACC / MAIN_ACC / ADMIN can create products */
    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<ReturnProductResponse> createProduct(@Valid @RequestBody ReturnProductRequest req) {
        return ResponseEntity.ok(inventoryService.createProduct(req));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        inventoryService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<ReturnProductResponse> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ReturnProductRequest req) {
        return ResponseEntity.ok(inventoryService.updateProduct(id, req));
    }

    // ── Opening stock (ADMIN direct — no approval) ────────────────

    @PostMapping("/opening-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addOpeningStock(@Valid @RequestBody SubmitStockInRequest request) {
        inventoryService.addOpeningStock(request);
        return ResponseEntity.noContent().build();
    }

    // ── Stock-in requests (ACC/MAIN_ACC submit, ADMIN approves) ──

    @PostMapping("/stock-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<StockInRequestResponse> submitStockIn(@Valid @RequestBody SubmitStockInRequest request) {
        return ResponseEntity.ok(inventoryService.submitStockIn(request));
    }

    @GetMapping("/stock-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockInRequestResponse>> getAllStockIn() {
        return ResponseEntity.ok(inventoryService.getAllStockInRequests());
    }

    @GetMapping("/stock-in/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockInRequestResponse>> getPendingStockIn() {
        return ResponseEntity.ok(inventoryService.getPendingStockInRequests());
    }

    @PatchMapping("/stock-in/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveStockIn(@PathVariable Long id) {
        inventoryService.approveStockIn(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/stock-in/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectStockIn(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        inventoryService.rejectStockIn(id, reason);
        return ResponseEntity.noContent().build();
    }
}
