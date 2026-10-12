package com.multi.finance.repository;

import com.multi.finance.entity.ExpenseAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface ExpenseAdvanceRepository extends JpaRepository<ExpenseAdvance, Long> {
    List<ExpenseAdvance> findAllByOrderByActivityDateDescCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM ExpenseAdvance a WHERE a.status = 'OPEN'")
    BigDecimal totalOutstanding();
}
