package com.multi.finance.repository;

import com.multi.finance.entity.WorkerAttendance;
import com.multi.finance.enums.AttendanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkerAttendanceRepository extends JpaRepository<WorkerAttendance, Long> {

    Optional<WorkerAttendance> findByWorkerIdAndAttendanceDate(Long workerId, LocalDate date);

    List<WorkerAttendance> findByAttendanceDateOrderByWorkerFullNameAsc(LocalDate date);

    List<WorkerAttendance> findByWorkerIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long workerId, LocalDate from, LocalDate to);

    @Query("SELECT a FROM WorkerAttendance a JOIN FETCH a.worker " +
           "WHERE a.attendanceDate BETWEEN :from AND :to " +
           "ORDER BY a.attendanceDate ASC, a.worker.fullName ASC")
    List<WorkerAttendance> findByDateRangeWithWorkers(
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a FROM WorkerAttendance a WHERE a.worker.id = :workerId " +
           "AND a.attendanceDate BETWEEN :from AND :to " +
           "AND a.attendanceType IN :types")
    List<WorkerAttendance> findByWorkerAndDateRangeAndTypes(
            @Param("workerId") Long workerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("types") List<AttendanceType> types);
}
