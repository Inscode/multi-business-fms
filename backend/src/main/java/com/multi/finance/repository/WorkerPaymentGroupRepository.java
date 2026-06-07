package com.multi.finance.repository;

import com.multi.finance.entity.WorkerPaymentGroup;
import com.multi.finance.enums.WorkerPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkerPaymentGroupRepository extends JpaRepository<WorkerPaymentGroup, Long> {

    List<WorkerPaymentGroup> findByWorkerId(Long workerId);

    @Query("SELECT g FROM WorkerPaymentGroup g JOIN FETCH g.worker " +
           "WHERE g.status = :status ORDER BY g.createdAt DESC")
    List<WorkerPaymentGroup> findByStatusWithWorker(@Param("status") WorkerPaymentStatus status);

    @Query("SELECT g FROM WorkerPaymentGroup g JOIN FETCH g.worker ORDER BY g.createdAt DESC")
    List<WorkerPaymentGroup> findAllWithWorker();
}
