package com.multi.finance.repository;

import com.multi.finance.entity.WorkerBillVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkerBillVisitRepository extends JpaRepository<WorkerBillVisit, Long> {

    Optional<WorkerBillVisit> findByBillIdAndWorkerId(Long billId, Long workerId);

    @Query("SELECT v FROM WorkerBillVisit v WHERE v.bill.id IN :billIds AND v.worker.id = :workerId")
    List<WorkerBillVisit> findByBillIdsAndWorkerId(@Param("billIds") List<Long> billIds,
                                                    @Param("workerId") Long workerId);

    // For admin/acc view — all visits for a specific bill
    List<WorkerBillVisit> findByBillId(Long billId);
}
