package com.multi.finance.repository;

import com.multi.finance.entity.BillChecklistClosing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillChecklistClosingRepository extends JpaRepository<BillChecklistClosing, Long> {

    Optional<BillChecklistClosing> findByItemNameAndClosingDate(String itemName, LocalDate closingDate);

    List<BillChecklistClosing> findByClosingDateOrderByItemNameAsc(LocalDate closingDate);

    /** Sum of markedEntered per item for all dates strictly before the given date */
    @Query("SELECT c.itemName, SUM(c.markedEntered) FROM BillChecklistClosing c " +
           "WHERE c.closingDate < :date GROUP BY c.itemName")
    List<Object[]> sumEnteredByItemBeforeDate(@Param("date") LocalDate date);

    /** Sum of markedEntered for today (used in dashboard summary) */
    @Query("SELECT COALESCE(SUM(c.markedEntered), 0) FROM BillChecklistClosing c WHERE c.closingDate = :date")
    int totalEnteredForDate(@Param("date") LocalDate date);
}
