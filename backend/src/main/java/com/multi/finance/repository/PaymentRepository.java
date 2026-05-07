package com.multi.finance.repository;
import com.multi.finance.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillId(Long billId);
    List<Payment> findByStatus(String status);
    List<Payment> findByPaymentDate(LocalDate date);
    List<Payment> findByBillIdAndStatus(Long billId, String status);
}
