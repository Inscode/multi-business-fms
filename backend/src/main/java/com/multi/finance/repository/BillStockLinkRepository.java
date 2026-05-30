package com.multi.finance.repository;

import com.multi.finance.entity.BillStockLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillStockLinkRepository extends JpaRepository<BillStockLink, Long> {

    List<BillStockLink> findBySystemBillId(Long systemBillId);

    List<BillStockLink> findByChildBillId(Long childBillId);

    Optional<BillStockLink> findBySystemBillIdAndChildBillId(Long systemBillId, Long childBillId);

    @Query("SELECT bsl FROM BillStockLink bsl WHERE bsl.childBill.id = :childBillId AND bsl.childBill.billSource IN ('DRAFT', 'MANUAL')")
    Optional<BillStockLink> findLinkForDraftOrManualBill(@Param("childBillId") Long childBillId);

    boolean existsByChildBillId(Long childBillId);
}
