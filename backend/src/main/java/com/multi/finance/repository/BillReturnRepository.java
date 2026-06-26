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
}