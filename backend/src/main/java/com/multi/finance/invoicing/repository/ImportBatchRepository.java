package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findTop50ByOrderByImportedAtDesc();
}
