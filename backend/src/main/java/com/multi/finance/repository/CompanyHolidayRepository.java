package com.multi.finance.repository;

import com.multi.finance.entity.CompanyHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {

    List<CompanyHoliday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    @Query("SELECT h FROM CompanyHoliday h LEFT JOIN FETCH h.specificWorkers " +
           "WHERE h.holidayDate BETWEEN :from AND :to ORDER BY h.holidayDate ASC")
    List<CompanyHoliday> findWithWorkersByDateRange(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT h FROM CompanyHoliday h LEFT JOIN FETCH h.specificWorkers " +
           "WHERE h.holidayDate BETWEEN :from AND :to " +
           "AND (h.appliesToAll = true OR EXISTS (" +
           "  SELECT 1 FROM h.specificWorkers w WHERE w.id = :workerId)) " +
           "ORDER BY h.holidayDate ASC")
    List<CompanyHoliday> findHolidaysForWorker(
            @Param("workerId") Long workerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
