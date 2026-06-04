package com.multi.finance.repository;

import com.multi.finance.entity.BillChecklistNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillChecklistNoteRepository extends JpaRepository<BillChecklistNote, Long> {

    List<BillChecklistNote> findByEntryDateOrderByCreatedAtAsc(LocalDate date);

    /** Sum of counts per item for a specific date — returns [itemName, sum] pairs */
    @Query("SELECT n.itemName, SUM(n.count) FROM BillChecklistNote n " +
           "WHERE n.entryDate = :date GROUP BY n.itemName")
    List<Object[]> sumByItemForDate(@Param("date") LocalDate date);

    /** Sum of counts per item for all dates strictly before the given date */
    @Query("SELECT n.itemName, SUM(n.count) FROM BillChecklistNote n " +
           "WHERE n.entryDate < :date GROUP BY n.itemName")
    List<Object[]> sumByItemBeforeDate(@Param("date") LocalDate date);

    /** Earliest entry date for an item before the given date (used for carry-from label) */
    @Query("SELECT MIN(n.entryDate) FROM BillChecklistNote n " +
           "WHERE n.itemName = :itemName AND n.entryDate < :date")
    LocalDate findEarliestDateBeforeDate(@Param("itemName") String itemName, @Param("date") LocalDate date);

    /** Count of distinct items that have any notes on the given date */
    @Query("SELECT COUNT(DISTINCT n.itemName) FROM BillChecklistNote n WHERE n.entryDate = :date")
    long countDistinctItemsForDate(@Param("date") LocalDate date);
}
