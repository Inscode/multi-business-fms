package com.multi.finance.repository;

import com.multi.finance.entity.BillBackorderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillBackorderItemRepository extends JpaRepository<BillBackorderItem, Long> {
    List<BillBackorderItem> findByRequestId(Long requestId);
}
