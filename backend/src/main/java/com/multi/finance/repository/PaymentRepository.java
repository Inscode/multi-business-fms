package com.multi.finance.repository;
import com.multi.finance.entity.Payment;
import com.multi.finance.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillId(Long billId);
    List<Payment> findByStatus(PaymentStatus status);
    long countByStatus(PaymentStatus status);
    List<Payment> findByPaymentDate(LocalDate date);
    List<Payment> findByBillIdAndStatus(Long billId, String status);
    long countByStatusAndConfirmedAtBetween(
            PaymentStatus status, LocalDateTime start, LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p " + "WHERE p.status = 'CONFIRMED' AND p.confirmedAt BETWEEN :start AND :end")
    BigDecimal sumConfirmedAmountBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
}
