package com.multi.finance.repository;

import com.multi.finance.entity.SummaryLoadBillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryLoadBillItemRepository extends JpaRepository<SummaryLoadBillItem, Long> {
    List<SummaryLoadBillItem> findBySummaryLoadBillId(Long summaryLoadBillId);
    Optional<SummaryLoadBillItem> findByIdAndSummaryLoadBillId(Long id, Long summaryLoadBillId);
}
