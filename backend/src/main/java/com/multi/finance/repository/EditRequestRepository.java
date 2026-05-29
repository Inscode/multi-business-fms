package com.multi.finance.repository;

import com.multi.finance.entity.EditRequest;
import com.multi.finance.enums.EditRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditRequestRepository extends JpaRepository<EditRequest, Long> {
    List<EditRequest> findByStatusOrderByRequestedAtDesc(EditRequestStatus status);
    List<EditRequest> findAllByOrderByRequestedAtDesc();
}