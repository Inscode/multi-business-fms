package com.multi.finance.repository;

import com.multi.finance.entity.SalaryPayment;
import com.multi.finance.enums.SalaryPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {

    List<SalaryPayment> findAllByOrderByCreatedAtDesc();

    List<SalaryPayment> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<SalaryPayment> findByMonthOrderByCreatedAtDesc(String month);

    List<SalaryPayment> findByStatusOrderByCreatedAtDesc(SalaryPaymentStatus status);

    long countByStatus(SalaryPaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SalaryPayment p " +
           "WHERE p.recipient.id = :recipientId AND p.month = :month " +
           "AND p.status IN ('RECORDED', 'APPROVED')")
    BigDecimal sumPaidForMonth(@Param("recipientId") Long recipientId, @Param("month") String month);

    boolean existsByRecipientIdAndMonthAndStatusIn(Long recipientId, String month, List<SalaryPaymentStatus> statuses);

    List<SalaryPayment> findByPaymentDateBetweenOrderByPaymentDateDesc(LocalDate from, LocalDate to);
}