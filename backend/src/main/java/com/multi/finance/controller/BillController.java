package com.multi.finance.controller;


import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
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
@CrossOrigin(origins = "*")
public class BillController {
    private final BillServiceImpl billService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BillResponse> createBill(
            @Valid @RequestBody BillRequest request) {
        return ResponseEntity.ok(billService.createBill(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getAllBills(
            @RequestParam(required = false) String business
    ) {
        return ResponseEntity.ok(billService.getTodaysBills(business));
    }

    @GetMapping("/today/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getTodaysBills(
            @PathVariable String business) {
        return ResponseEntity.ok(billService.getTodaysBills(business));
    }

    @GetMapping("/unconfirmed/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<BillResponse>> getUnconfirmedBills(
            @PathVariable String business) {
        return ResponseEntity.ok(billService.getUnconfirmedBills(business));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BillResponse> assignBill(
            @PathVariable Long id,
            @Valid @RequestBody AssignBillRequest request) {
        return ResponseEntity.ok(billService.assignBill(id, request));
    }

    @PatchMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BillResponse> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markReceived(id));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillResponse> confirmBill(@PathVariable Long id) {
        return ResponseEntity.ok(billService.confirmBill(id));
    }


}
