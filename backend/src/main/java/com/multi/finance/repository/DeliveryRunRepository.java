package com.multi.finance.repository;

import com.multi.finance.entity.DeliveryRun;
import com.multi.finance.enums.DeliveryRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeliveryRunRepository extends JpaRepository<DeliveryRun, Long> {

    @Query("SELECT r FROM DeliveryRun r WHERE r.status = :status "
         + "ORDER BY r.plannedDate DESC")
    List<DeliveryRun> findByStatusWithArea(@Param("status") DeliveryRunStatus status);

    @Query("SELECT r FROM DeliveryRun r "
         + "WHERE r.plannedDate BETWEEN :from AND :to ORDER BY r.plannedDate DESC, r.id DESC")
    List<DeliveryRun> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT r FROM DeliveryRun r WHERE r.id = :id")
    Optional<DeliveryRun> findByIdWithArea(@Param("id") Long id);

    /** Rounds counting against one month, whatever date they actually went out. */
    @Query("SELECT r FROM DeliveryRun r WHERE r.runMonth = :month "
         + "ORDER BY r.plannedDate DESC, r.id DESC")
    List<DeliveryRun> findByMonth(@Param("month") LocalDate month);

    /**
     * The run bills are currently being entered into, whoever opened it.
     *
     * <p>A lorry is loading whether or not the person at the screen is the one who said
     * so. Scoped to the opener at first, on the grounds that one accountant enters them
     * all — but an admin entering a bill then saw no run at all, and since a delivery
     * has to be given, was pushed into calling route goods a store pickup.
     *
     * <p>Their own run still comes first where several are open, because that is the one
     * they are working through; anyone else's follows, newest first.
     */
    @Query("SELECT r FROM DeliveryRun r "
         + "WHERE r.status = com.multi.finance.enums.DeliveryRunStatus.OPEN "
         + "ORDER BY CASE WHEN r.openedBy = :user THEN 0 ELSE 1 END, r.openedAt DESC")
    List<DeliveryRun> findOpenFor(@Param("user") String user);

    /** Open runs on that date already covering the given area. */
    @Query("SELECT r FROM DeliveryRun r JOIN r.areas a "
         + "WHERE a.id = :areaId AND r.plannedDate = :date "
         + "AND r.status = com.multi.finance.enums.DeliveryRunStatus.OPEN")
    List<DeliveryRun> findOpenCovering(@Param("areaId") Long areaId,
                                       @Param("date") LocalDate date);
}
