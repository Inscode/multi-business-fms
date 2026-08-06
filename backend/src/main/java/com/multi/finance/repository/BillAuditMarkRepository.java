package com.multi.finance.repository;

import com.multi.finance.entity.BillAuditMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillAuditMarkRepository extends JpaRepository<BillAuditMark, Long> {

    @Query("SELECT m FROM BillAuditMark m JOIN FETCH m.bill WHERE m.session.id = :sessionId")
    List<BillAuditMark> findBySessionIdWithBill(@Param("sessionId") Long sessionId);

    Optional<BillAuditMark> findBySessionIdAndBillId(Long sessionId, Long billId);

    void deleteBySessionIdAndBillId(Long sessionId, Long billId);
}
