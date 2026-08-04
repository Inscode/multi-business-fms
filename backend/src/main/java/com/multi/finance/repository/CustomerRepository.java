package com.multi.finance.repository;

import com.multi.finance.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByActiveTrueOrderByNameAsc();
    List<Customer> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);

    // Used by the invoicing module (Ventura import customer resolution)
    Optional<Customer> findByCustomerCode(String customerCode);
    List<Customer> findByNameContainingIgnoreCaseAndActiveTrue(String name);
}
