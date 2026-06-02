package com.multi.finance.repository;

import com.multi.finance.entity.WorkerTabPurchase;
import com.multi.finance.enums.WorkerFinanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface WorkerTabPurchaseRepository extends JpaRepository<WorkerTabPurchase, Long> {

    List<WorkerTabPurchase> findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus status);

    List<WorkerTabPurchase> findByRecipientIdAndMonthOrderByCreatedAtDesc(Long recipientId, String month);

    List<WorkerTabPurchase> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<WorkerTabPurchase> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(w.totalAmount), 0) FROM WorkerTabPurchase w " +
           "WHERE w.recipient.id = :recipientId AND w.month = :month AND w.status = 'PAID'")
    BigDecimal sumPaidForMonth(@Param("recipientId") Long recipientId, @Param("month") String month);

    long countByStatus(WorkerFinanceStatus status);

    List<WorkerTabPurchase> findByStatusAndPurchaseDateBetweenOrderByPurchaseDateDesc(
            WorkerFinanceStatus status, LocalDate from, LocalDate to);
}
