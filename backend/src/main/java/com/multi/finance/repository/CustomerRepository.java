package com.multi.finance.repository;

import com.multi.finance.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByActiveTrueOrderByNameAsc();
    List<Customer> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
