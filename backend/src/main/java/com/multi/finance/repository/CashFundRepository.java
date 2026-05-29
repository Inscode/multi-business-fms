package com.multi.finance.repository;

import com.multi.finance.entity.CashFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CashFundRepository extends JpaRepository<CashFund, Long> {

    List<CashFund> findAllByOrderByDateDescCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashFund c")
    BigDecimal totalFunded();
}