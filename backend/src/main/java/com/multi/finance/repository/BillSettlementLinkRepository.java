package com.multi.finance.repository;

import com.multi.finance.entity.BillSettlementLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillSettlementLinkRepository extends JpaRepository<BillSettlementLink, Long> {

    /** Every hand-written bill collecting for this system bill. */
    @Query("SELECT l FROM BillSettlementLink l JOIN FETCH l.manualBill "
         + "WHERE l.systemBill.id = :systemBillId ORDER BY l.linkedAt")
    List<BillSettlementLink> findBySystemBillId(@Param("systemBillId") Long systemBillId);

    /** The system bill this hand-written bill collects for, if any. */
    Optional<BillSettlementLink> findByManualBillId(Long manualBillId);

    void deleteBySystemBillId(Long systemBillId);

    void deleteByManualBillId(Long manualBillId);
}
