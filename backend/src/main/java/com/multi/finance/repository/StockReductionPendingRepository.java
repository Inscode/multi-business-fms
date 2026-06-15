package com.multi.finance.repository;

import com.multi.finance.entity.StockReductionPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockReductionPendingRepository extends JpaRepository<StockReductionPending, Long> {

    List<StockReductionPending> findByStatusOrderBySubmittedAtDesc(StockReductionPending.ApprovalStatus status);

    List<StockReductionPending> findAllByOrderBySubmittedAtDesc();

    Optional<StockReductionPending> findByBillIdAndStatus(Long billId, StockReductionPending.ApprovalStatus status);

    boolean existsByBillIdAndStatus(Long billId, StockReductionPending.ApprovalStatus status);

    @Query("SELECT r.bill.id FROM StockReductionPending r WHERE r.status = 'PENDING'")
    Set<Long> findBillIdsWithPendingReduction();
}
