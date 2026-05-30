package com.multi.finance.repository;

import com.multi.finance.entity.SummaryLoadBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryLoadBillRepository extends JpaRepository<SummaryLoadBill, Long> {

    List<SummaryLoadBill> findByStatusOrderByCreatedAtDesc(SummaryLoadBill.ApprovalStatus status);

    List<SummaryLoadBill> findAllByOrderByCreatedAtDesc();

    @Query("SELECT slb FROM SummaryLoadBill slb JOIN slb.systemBills b WHERE b.id = :billId")
    Optional<SummaryLoadBill> findBySystemBillId(@Param("billId") Long billId);
}
