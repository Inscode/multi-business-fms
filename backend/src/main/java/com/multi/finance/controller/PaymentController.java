package com.multi.finance.controller;

import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.request.ReturnPaymentRequest;
import com.multi.finance.dto.response.PaymentResponse;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.service.impl.PaymentServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    @PostMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<PaymentResponse> enterPayment(
            @PathVariable Long billId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.ok(
                paymentService.enterPayment(billId, request)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<List<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(paymentService.getAllPayments(status));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.updatePayment(id, request));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirmPayment(id));
    }

    @GetMapping("/bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByBill(
            @PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.getPaymentsByBill(billId));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPendingConfirmations() {
        return ResponseEntity.ok(paymentService.getPendingConfirmations());
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<PaymentResponse>> getTodaysPayments() {
        return ResponseEntity.ok(paymentService.getTodaysPayments());
    }

    @PatchMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> markChequeReturned(
            @PathVariable Long id,
            @Valid @RequestBody ReturnPaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.markChequeReturned(id, request.getReturnReason()));
    }

}
