package com.multi.finance.repository;

import com.multi.finance.entity.SalaryRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryRecipientRepository extends JpaRepository<SalaryRecipient, Long> {
    List<SalaryRecipient> findByActiveTrueOrderByDesignationAscNameAsc();
    List<SalaryRecipient> findAllByOrderByDesignationAscNameAsc();
}