package com.multi.finance.repository;

import com.multi.finance.entity.Expense;
import com.multi.finance.enums.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    long countByStatus(ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e")
    java.math.BigDecimal totalSpent();
}