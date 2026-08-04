package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByItemIdOrderByCreatedAtDesc(Long itemId);
    List<StockMovement> findByReferenceIdAndReferenceType(Long referenceId, String referenceType);
}
