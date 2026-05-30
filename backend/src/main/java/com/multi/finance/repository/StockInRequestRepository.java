package com.multi.finance.repository;

import com.multi.finance.entity.StockInRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockInRequestRepository extends JpaRepository<StockInRequest, Long> {
    List<StockInRequest> findAllByOrderByCreatedAtDesc();
    List<StockInRequest> findByStatusOrderByCreatedAtDesc(StockInRequest.ApprovalStatus status);
}
