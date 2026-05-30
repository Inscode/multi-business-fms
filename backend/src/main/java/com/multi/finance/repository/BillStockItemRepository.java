package com.multi.finance.repository;

import com.multi.finance.entity.BillStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillStockItemRepository extends JpaRepository<BillStockItem, Long> {

    List<BillStockItem> findByBillId(Long billId);

    List<BillStockItem> findByProductId(Long productId);
}
