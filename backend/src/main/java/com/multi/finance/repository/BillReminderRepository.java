package com.multi.finance.repository;

import com.multi.finance.entity.BillReminder;
import com.multi.finance.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillReminderRepository extends JpaRepository<BillReminder, Long> {

    List<BillReminder> findByReminderDateAndStatus(LocalDate date, ReminderStatus status);

    List<BillReminder> findByReminderDateLessThanAndStatus(LocalDate date, ReminderStatus status);

    List<BillReminder> findByBillId(Long billId);

    List<BillReminder> findByStatus(ReminderStatus status);

    // ── Copilot queries ───────────────────────────────────────────────────────

    @Query("SELECT r FROM BillReminder r JOIN FETCH r.bill LEFT JOIN FETCH r.bill.customer JOIN FETCH r.createdBy WHERE r.bill.id IN :billIds ORDER BY r.reminderDate DESC")
    List<BillReminder> findByBillIdIn(@Param("billIds") List<Long> billIds);

    @Query("SELECT r FROM BillReminder r JOIN FETCH r.bill LEFT JOIN FETCH r.bill.customer JOIN FETCH r.createdBy WHERE r.reminderDate <= :today AND r.bill.fullyPaid = false ORDER BY r.reminderDate ASC")
    List<BillReminder> findDueRemindersForOutstanding(@Param("today") LocalDate today);
}