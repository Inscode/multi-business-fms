package com.multi.finance.repository;

import com.multi.finance.entity.BillAuditSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillAuditSessionRepository extends JpaRepository<BillAuditSession, Long> {

    List<BillAuditSession> findAllByOrderByOpenedAtDesc();

    /**
     * The open sweep for a month + scope. Scope columns are nullable, so the
     * comparison has to treat null and a value the same way.
     */
    @Query("SELECT s FROM BillAuditSession s WHERE s.periodMonth = :month " +
           "AND (:business IS NULL AND s.businessScope IS NULL OR s.businessScope = :business) " +
           "AND (:area IS NULL AND s.areaScope IS NULL OR s.areaScope = :area) " +
           "AND s.closedAt IS NULL")
    Optional<BillAuditSession> findOpenSession(@Param("month") LocalDate month,
                                               @Param("business") String business,
                                               @Param("area") String area);
}
