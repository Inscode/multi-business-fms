package com.multi.finance.repository;

import com.multi.finance.entity.PettyCashDeduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface PettyCashDeductionRepository extends JpaRepository<PettyCashDeduction, Long> {

    List<PettyCashDeduction> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM PettyCashDeduction d")
    BigDecimal totalDeducted();

    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
