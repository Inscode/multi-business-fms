package com.multi.finance.repository;

import com.multi.finance.entity.WorkerAdvanceBonus;
import com.multi.finance.enums.AdvanceBonusType;
import com.multi.finance.enums.WorkerFinanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface WorkerAdvanceBonusRepository extends JpaRepository<WorkerAdvanceBonus, Long> {

    List<WorkerAdvanceBonus> findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus status);

    List<WorkerAdvanceBonus> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<WorkerAdvanceBonus> findAllByOrderByCreatedAtDesc();

    List<WorkerAdvanceBonus> findByRecipientIdAndTypeAndFullyRecoveredFalseAndStatus(
            Long recipientId, AdvanceBonusType type, WorkerFinanceStatus status);

    @Query("SELECT COALESCE(SUM(w.amount - w.recoveredAmount), 0) FROM WorkerAdvanceBonus w " +
           "WHERE w.recipient.id = :recipientId AND w.type = 'ADVANCE' " +
           "AND w.status = 'PAID' AND w.fullyRecovered = false")
    BigDecimal totalOutstandingAdvance(@Param("recipientId") Long recipientId);

    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WorkerAdvanceBonus w " +
           "WHERE w.recipient.id = :recipientId AND w.month = :month AND w.type = 'BONUS' AND w.status = 'PAID'")
    BigDecimal sumBonusForMonth(@Param("recipientId") Long recipientId, @Param("month") String month);

    long countByStatus(WorkerFinanceStatus status);

    List<WorkerAdvanceBonus> findByStatusAndPaymentDateBetweenOrderByPaymentDateDesc(
            WorkerFinanceStatus status, LocalDate from, LocalDate to);
}
