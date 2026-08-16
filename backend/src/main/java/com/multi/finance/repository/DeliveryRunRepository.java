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
     * The run this accountant is currently entering bills into. One person enters them
     * all, so the newest open run they opened is the one the create-bill screen sticks
     * to.
     */
    @Query("SELECT r FROM DeliveryRun r "
         + "WHERE r.status = com.multi.finance.enums.DeliveryRunStatus.OPEN "
         + "AND r.openedBy = :user ORDER BY r.openedAt DESC")
    List<DeliveryRun> findOpenFor(@Param("user") String user);

    /** Open runs on that date already covering the given area. */
    @Query("SELECT r FROM DeliveryRun r JOIN r.areas a "
         + "WHERE a.id = :areaId AND r.plannedDate = :date "
         + "AND r.status = com.multi.finance.enums.DeliveryRunStatus.OPEN")
    List<DeliveryRun> findOpenCovering(@Param("areaId") Long areaId,
                                       @Param("date") LocalDate date);
}
