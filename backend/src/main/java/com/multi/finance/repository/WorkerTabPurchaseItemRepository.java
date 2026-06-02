package com.multi.finance.repository;

import com.multi.finance.entity.WorkerTabPurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerTabPurchaseItemRepository extends JpaRepository<WorkerTabPurchaseItem, Long> {
    List<WorkerTabPurchaseItem> findByPurchaseIdOrderByIdAsc(Long purchaseId);
}
