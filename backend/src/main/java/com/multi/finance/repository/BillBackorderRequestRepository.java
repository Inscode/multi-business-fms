package com.multi.finance.repository;

import com.multi.finance.entity.BillBackorderRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillBackorderRequestRepository extends JpaRepository<BillBackorderRequest, Long> {
    List<BillBackorderRequest> findAllByOrderBySubmittedAtDesc();
    List<BillBackorderRequest> findByBillId(Long billId);
    boolean existsByBillIdAndStatus(Long billId, BillBackorderRequest.ApprovalStatus status);
}
