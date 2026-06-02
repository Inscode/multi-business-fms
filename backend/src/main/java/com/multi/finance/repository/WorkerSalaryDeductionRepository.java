package com.multi.finance.repository;

import com.multi.finance.entity.WorkerSalaryDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface WorkerSalaryDeductionRepository extends JpaRepository<WorkerSalaryDeduction, Long> {

    List<WorkerSalaryDeduction> findByRecipientIdAndDeductionMonthOrderByCreatedAtAsc(
            Long recipientId, String deductionMonth);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM WorkerSalaryDeduction d " +
           "WHERE d.recipient.id = :recipientId AND d.deductionMonth = :month")
    BigDecimal totalForMonth(@Param("recipientId") Long recipientId, @Param("month") String month);

    boolean existsByDeductionTypeAndReferenceId(String deductionType, Long referenceId);
}
