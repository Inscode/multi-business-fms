package com.multi.finance.repository;

import com.multi.finance.entity.Worker;
import org.hibernate.jdbc.Work;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findByActiveTrue();
    List<Worker> findByWorkerType(String workerType);
}
