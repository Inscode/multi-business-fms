package com.multi.finance.repository;

import com.multi.finance.entity.BillReminder;
import com.multi.finance.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BillReminderRepository extends JpaRepository<BillReminder, Long> {

    List<BillReminder> findByReminderDateAndStatus(LocalDate date, ReminderStatus status);

    List<BillReminder> findByReminderDateLessThanAndStatus(LocalDate date, ReminderStatus status);

    List<BillReminder> findByBillId(Long billId);

    List<BillReminder> findByStatus(ReminderStatus status);
}