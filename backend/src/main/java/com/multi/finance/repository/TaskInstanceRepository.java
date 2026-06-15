package com.multi.finance.repository;

import com.multi.finance.entity.TaskInstance;
import com.multi.finance.entity.TaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskInstanceRepository extends JpaRepository<TaskInstance, Long> {

    // Instances for a date + MOVED instances whose moved_to_date is that date
    @Query("""
        SELECT i FROM TaskInstance i
        JOIN FETCH i.template
        WHERE i.date = :date
           OR (i.status = 'MOVED' AND i.movedToDate = :date)
        ORDER BY i.createdAt ASC
        """)
    List<TaskInstance> findAllForDate(@Param("date") LocalDate date);

    boolean existsByTemplateAndDate(TaskTemplate template, LocalDate date);

    // Date range for history view — live table only (recent dates)
    @Query("""
        SELECT i FROM TaskInstance i
        JOIN FETCH i.template
        WHERE i.date BETWEEN :from AND :to
        ORDER BY i.date DESC, i.createdAt ASC
        """)
    List<TaskInstance> findForDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // Find old instances eligible for archiving (used by manual trigger if needed)
    @Query("""
        SELECT i FROM TaskInstance i
        WHERE i.date < :cutoff
          AND i.status IN ('COMPLETED', 'MOVED')
        """)
    List<TaskInstance> findArchiveable(@Param("cutoff") LocalDate cutoff);
}
