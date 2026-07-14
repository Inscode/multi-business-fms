package com.multi.finance.repository;

import com.multi.finance.entity.BillReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface BillReviewRepository extends JpaRepository<BillReview, Long> {

    @Query("SELECT r.bill.id FROM BillReview r WHERE r.reviewedBy.id = :userId")
    Set<Long> findReviewedBillIdsByUserId(@Param("userId") Long userId);

    boolean existsByBillIdAndReviewedById(Long billId, Long reviewedById);
}
