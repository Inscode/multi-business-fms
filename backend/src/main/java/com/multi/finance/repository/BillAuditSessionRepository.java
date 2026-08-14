package com.multi.finance.repository;

import com.multi.finance.entity.BillAuditSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillAuditSessionRepository extends JpaRepository<BillAuditSession, Long> {

    List<BillAuditSession> findAllByOrderByOpenedAtDesc();

    /**
     * A user's own open sweep for a month. Sweeps are per person — you tick your own
     * copy and can view (but not change) anyone else's. Business and area remain view
     * filters, never separate sweeps, so narrowing never hides marks already made.
     */
    @Query("SELECT s FROM BillAuditSession s WHERE s.periodMonth = :month " +
           "AND s.openedBy.id = :userId AND s.closedAt IS NULL ORDER BY s.openedAt ASC")
    List<BillAuditSession> findOpenSessions(@Param("month") LocalDate month,
                                            @Param("userId") Long userId);
}
