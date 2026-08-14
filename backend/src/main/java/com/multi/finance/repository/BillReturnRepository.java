package com.multi.finance.repository;

import com.multi.finance.entity.BillReturn;
import com.multi.finance.enums.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillReturnRepository extends JpaRepository<BillReturn, Long> {
    List<BillReturn> findByBillIdOrderBySubmittedAtDesc(Long billId);
    List<BillReturn> findByStatusOrderBySubmittedAtDesc(ReturnStatus status);
    List<BillReturn> findAllByOrderBySubmittedAtDesc();
    long countByStatus(ReturnStatus status);

    @Query("SELECT r FROM BillReturn r LEFT JOIN FETCH r.items WHERE r.id = :id")
    Optional<BillReturn> findByIdWithItems(@Param("id") Long id);

    List<BillReturn> findByStatusAndBillAmountAdjustedFalse(ReturnStatus status);

    @Query("SELECT r FROM BillReturn r LEFT JOIN FETCH r.items WHERE r.bill.id = :billId "
         + "ORDER BY r.submittedAt DESC")
    List<BillReturn> findByBillIdWithItems(@Param("billId") Long billId);

    /**
     * Returns still awaiting someone's action on a bill. Payment entry checks this, so
     * an unconfirmed return cannot be quietly walked past while cash is collected.
     */
    @Query("SELECT r FROM BillReturn r WHERE r.bill.id = :billId AND r.status IN :statuses")
    List<BillReturn> findByBillIdAndStatusIn(@Param("billId") Long billId,
                                             @Param("statuses") List<ReturnStatus> statuses);

    @Query("SELECT r FROM BillReturn r LEFT JOIN FETCH r.items WHERE r.status IN :statuses "
         + "ORDER BY r.submittedAt DESC")
    List<BillReturn> findByStatusInWithItems(@Param("statuses") List<ReturnStatus> statuses);

    long countByStatusIn(List<ReturnStatus> statuses);

    /** How much a bill is currently being reduced by, summed from its live returns. */
    @Query("SELECT COALESCE(SUM(r.approvedAmount), 0) FROM BillReturn r "
         + "WHERE r.bill.id = :billId AND r.status = com.multi.finance.enums.ReturnStatus.APPROVED "
         + "AND r.legacyAmountAdjusted = false")
    java.math.BigDecimal sumActiveReturns(@Param("billId") Long billId);

    /** Quantity of an invoice line already claimed back, so a return can't exceed it. */
    @Query("SELECT COALESCE(SUM(i.quantityRequested), 0) FROM BillReturnItem i "
         + "WHERE i.invoiceLine.id = :lineId "
         + "AND i.billReturn.status NOT IN (com.multi.finance.enums.ReturnStatus.REJECTED, "
         + "com.multi.finance.enums.ReturnStatus.CANCELLED, "
         + "com.multi.finance.enums.ReturnStatus.NOT_RECEIVED)")
    Integer sumClaimedQtyForLine(@Param("lineId") Long lineId);
}