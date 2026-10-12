package com.multi.finance.repository;

import com.multi.finance.entity.ExpenseBusFare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseBusFareRepository extends JpaRepository<ExpenseBusFare, Long> {
    List<ExpenseBusFare> findByActiveTrueOrderByFromLocationAscToLocationAsc();
    boolean existsByFromLocationIgnoreCaseAndToLocationIgnoreCase(String fromLocation, String toLocation);
    boolean existsByFromLocationIgnoreCaseAndToLocationIgnoreCaseAndIdNot(String fromLocation, String toLocation, Long id);
}
