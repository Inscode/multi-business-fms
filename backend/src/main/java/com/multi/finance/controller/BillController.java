package com.multi.finance.controller;


import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.DashboardResponse;
import com.multi.finance.dto.response.DashboardStatsResponse;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.BillServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillServiceImpl billService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillResponse> createBill(
            @Valid @RequestBody BillRequest request) {
        return ResponseEntity.ok(billService.createBill(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getAllBills(
            @RequestParam(required = false) BusinessType business,
            @RequestParam(required = false) BillStatus status) {
        return ResponseEntity.ok(billService.getAllBills(business, status));
    }


    @GetMapping("/today/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getTodaysBills(
            @PathVariable BusinessType business) {
        return ResponseEntity.ok(billService.getTodaysBills(business));
    }

    @GetMapping("/unconfirmed/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getUnconfirmedBills(
            @PathVariable BusinessType business) {
        return ResponseEntity.ok(billService.getUnconfirmedBills(business));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> updateBill(
            @PathVariable Long id,
            @RequestBody BillRequest request) {
        return ResponseEntity.ok(billService.updateBill(id, request));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> assignBill(
            @PathVariable Long id,
            @Valid @RequestBody AssignBillRequest request) {
        return ResponseEntity.ok(billService.assignBill(id, request));
    }

    @PatchMapping("/{id}/shop-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> markShopReceived(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markShopReceived(id));
    }

    @PatchMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillResponse> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markReceived(id));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> markCompleted(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markCompleted(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billService.getBillById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        billService.deleteBill(id);
        return ResponseEntity.noContent().build();
    }

}
