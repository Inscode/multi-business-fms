package com.multi.finance.repository;

import com.multi.finance.entity.WorkerPaymentEntry;
import com.multi.finance.enums.WorkerPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface WorkerPaymentEntryRepository extends JpaRepository<WorkerPaymentEntry, Long> {

    List<WorkerPaymentEntry> findByBillId(Long billId);

    List<WorkerPaymentEntry> findByWorkerId(Long workerId);

    List<WorkerPaymentEntry> findByWorkerIdAndEnteredAtBetweenOrderByEnteredAtDesc(
            Long workerId, LocalDateTime from, LocalDateTime to);

    List<WorkerPaymentEntry> findByStatusOrderByCreatedAtDesc(WorkerPaymentStatus status);

    List<WorkerPaymentEntry> findByBillIdAndStatusNot(Long billId, WorkerPaymentStatus status);

    List<WorkerPaymentEntry> findByGroupId(Long groupId);

    // Temp balance: sum of PENDING entries for a bill
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM WorkerPaymentEntry e " +
           "WHERE e.bill.id = :billId AND e.status = 'PENDING'")
    BigDecimal sumPendingForBill(@Param("billId") Long billId);

    // For owner/admin collections view
    @Query("SELECT e FROM WorkerPaymentEntry e JOIN FETCH e.bill JOIN FETCH e.worker " +
           "ORDER BY e.createdAt DESC")
    List<WorkerPaymentEntry> findAllWithDetails();

    @Query("SELECT e FROM WorkerPaymentEntry e JOIN FETCH e.bill JOIN FETCH e.worker " +
           "WHERE e.status = :status ORDER BY e.createdAt DESC")
    List<WorkerPaymentEntry> findByStatusWithDetails(@Param("status") WorkerPaymentStatus status);
}
