package com.multi.finance.service.impl;


import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.response.PaymentResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Payment;
import com.multi.finance.entity.User;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.aspectj.apache.bcel.classfile.annotation.RuntimeInvisTypeAnnos;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;

    @Transactional
    public PaymentResponse enterPayment(PaymentRequest request) {
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        if (bill.getFullyPaid()) {
            throw new RuntimeException("Bill is already fully paid");
        }

        if (request.getAmount().compareTo(bill.getBalanceRemaining()) > 0) {
            throw new RuntimeException("Payment amount exceeds balance remaining of Rs. " + bill.getBalanceRemaining());
        }

        Payment payment = Payment.builder()
                .bill(bill)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .status("PENDING_CONFIRMATION")
                .enteredBy(getCurrentUser())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        return toResponse(payment, bill);

    }

    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (!payment.getStatus().equals("PENDING_CONFIRMATION")) {
            throw new RuntimeException("Payment is already confirmed");
        }

        Bill bill = payment.getBill();

        // Update payment
        payment.setStatus("CONFIRMED");
        payment.setConfirmedBy(getCurrentUser());
        payment.setConfirmedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Update bill totals
        BigDecimal newAmountPaid = bill.getAmountPaid().add(payment.getAmount());
        BigDecimal newBalance = bill.getTotalAmount().subtract(newAmountPaid);
        boolean fullyPaid = newBalance.compareTo(BigDecimal.ZERO) <= 0;

        bill.setAmountPaid(newAmountPaid);
        bill.setBalanceRemaining(newBalance.max(BigDecimal.ZERO));
        bill.setFullyPaid(fullyPaid);
        if (fullyPaid) {
            bill.setStatus("CONFIRMED");
            bill.setConfirmedBy(getCurrentUser());
            bill.setConfirmedAt(LocalDateTime.now());
        }
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);

        return toResponse(payment, bill);
    }

    public List<PaymentResponse> getPaymentsByBill(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        return paymentRepository.findByBillId(billId)
                .stream()
                .map(p -> toResponse(p, bill))
                .toList();
    }

    public List<PaymentResponse> getPendingConfirmations() {
        return paymentRepository.findByStatus("PENDING_CONFIRMATION")
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    public List<PaymentResponse> getTodaysPayments() {
        return paymentRepository.findByPaymentDate(LocalDate.now())
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private PaymentResponse toResponse(Payment payment, Bill bill) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .billId(bill.getId())
                .customerName(bill.getCustomerName())
                .business(bill.getBusiness())
                .billTotal(bill.getTotalAmount())
                .amountPaid(bill.getAmountPaid())
                .balanceRemaining(bill.getBalanceRemaining())
                .fullyPaid(bill.getFullyPaid())
                .paymentAmount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .enteredByName(payment.getEnteredBy() != null ? payment.getEnteredBy().getFullName() : null)
                .confirmedByName(payment.getConfirmedBy() != null ? payment.getConfirmedBy().getFullName() : null)
                .confirmedAt(payment.getConfirmedAt())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
