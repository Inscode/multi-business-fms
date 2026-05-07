package com.multi.finance.controller;

import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.response.PaymentResponse;
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
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<PaymentResponse> enterPayment(
            @Valid @RequestBody PaymentRequest request
            ) {
        return ResponseEntity.ok(paymentService.enterPayment(request));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirmPayment(id));
    }

    @GetMapping("/bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT', 'OWNER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<PaymentResponse>> getTodaysPayments() {
        return ResponseEntity.ok(paymentService.getTodaysPayments());
    }

}
