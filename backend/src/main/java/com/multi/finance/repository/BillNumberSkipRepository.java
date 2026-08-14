package com.multi.finance.repository;

import com.multi.finance.entity.BillNumberSkip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BillNumberSkipRepository extends JpaRepository<BillNumberSkip, Long> {

    // Only APPROVED skips affect the next-number suggestion
    @Query(value = "SELECT COALESCE(MAX(CAST(bill_number AS INTEGER)), 0) FROM bill_number_skips " +
                   "WHERE business = :business AND status = 'APPROVED' AND bill_number ~ '^[0-9]+$'", nativeQuery = true)
    Integer findMaxSkippedNumber(@Param("business") String business);

    // Shared book: PLASTIC + STATIONERY MANUAL and RAINCO MANUAL_BOOK all save skips under PLASTIC or STATIONERY
    @Query(value = "SELECT COALESCE(MAX(CAST(bill_number AS INTEGER)), 0) FROM bill_number_skips " +
                   "WHERE business IN ('PLASTIC','STATIONERY') AND status = 'APPROVED' AND bill_number ~ '^[0-9]+$'", nativeQuery = true)
    Integer findMaxSharedBookSkippedNumber();

    // Every approved skip, so a gap that was deliberately skipped isn't reported as missing.
    @Query(value = "SELECT CAST(bill_number AS INTEGER) FROM bill_number_skips " +
                   "WHERE business = :business AND status = 'APPROVED' AND bill_number ~ '^[0-9]+$'",
           nativeQuery = true)
    List<Integer> findApprovedSkippedNumbers(@Param("business") String business);

    @Query(value = "SELECT CAST(bill_number AS INTEGER) FROM bill_number_skips " +
                   "WHERE business IN ('PLASTIC','STATIONERY') AND status = 'APPROVED' " +
                   "AND bill_number ~ '^[0-9]+$'", nativeQuery = true)
    List<Integer> findApprovedSharedBookSkippedNumbers();

    // Pending skips for admin review — load with submittedBy and bill eagerly
    @Query("SELECT s FROM BillNumberSkip s LEFT JOIN FETCH s.submittedBy LEFT JOIN FETCH s.bill " +
           "WHERE s.status = 'PENDING' ORDER BY s.createdAt ASC")
    List<BillNumberSkip> findAllPending();
}
