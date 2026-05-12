package com.multi.finance.service.impl;


import com.multi.finance.dto.request.WorkerRequest;
import com.multi.finance.dto.response.WorkerResponse;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.WorkerType;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkerServiceImpl {
    private final WorkerRepository workerRepository;

    public WorkerResponse createWorker(WorkerRequest request) {
        Worker worker = Worker.builder()
                .fullName(request.getFullName())
                .workerType(request.getWorkerType())
                .baseSalary(request.getBaseSalary())
                .active(true)
                .joinedDate(request.getJoinedDate())
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return toResponse(workerRepository.save(worker));
    }

    public List<WorkerResponse> getAllWorkers() {
        return workerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<WorkerResponse> getWorkersByWorkerType(WorkerType workerType) {
        return workerRepository.findByWorkerType(workerType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkerResponse updateWorker(Long id, WorkerRequest request) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        worker.setFullName(request.getFullName());
        worker.setWorkerType(request.getWorkerType());
        worker.setBaseSalary(request.getBaseSalary());
        worker.setJoinedDate(request.getJoinedDate());
        worker.setNotes(request.getNotes());
        worker.setUpdatedAt(LocalDateTime.now());
        return toResponse(workerRepository.save(worker));

    }

    public void activateWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        worker.setActive(true);
        worker.setUpdatedAt(LocalDateTime.now());
        workerRepository.save(worker);
    }

    public void deactivateWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        worker.setActive(false);
        worker.setUpdatedAt(LocalDateTime.now());
        workerRepository.save(worker);
    }

    private WorkerResponse toResponse(Worker worker) {
        return WorkerResponse.builder()
                .id(worker.getId())
                .fullName(worker.getFullName())
                .workerType(worker.getWorkerType())
                .baseSalary(worker.getBaseSalary())
                .active(worker.getActive())
                .joinedDate(worker.getJoinedDate())
                .notes(worker.getNotes())
                .createdAt(worker.getCreatedAt())
                .build();

    }
}
