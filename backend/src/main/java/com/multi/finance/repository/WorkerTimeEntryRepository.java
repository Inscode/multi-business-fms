package com.multi.finance.repository;

import com.multi.finance.entity.WorkerTimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkerTimeEntryRepository extends JpaRepository<WorkerTimeEntry, Long> {

    List<WorkerTimeEntry> findByWorkerIdAndEntryDateOrderByClockInAsc(Long workerId, LocalDate date);

    List<WorkerTimeEntry> findByEntryDateOrderByWorkerFullNameAscClockInAsc(LocalDate date);

    @Query("SELECT e FROM WorkerTimeEntry e JOIN FETCH e.worker " +
           "WHERE e.entryDate BETWEEN :from AND :to " +
           "ORDER BY e.entryDate ASC, e.worker.fullName ASC, e.clockIn ASC")
    List<WorkerTimeEntry> findByDateRangeWithWorkers(
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
