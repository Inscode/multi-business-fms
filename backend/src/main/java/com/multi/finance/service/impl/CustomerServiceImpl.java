package com.multi.finance.service.impl;

import com.multi.finance.dto.request.CustomerRequest;
import com.multi.finance.dto.response.CustomerResponse;
import com.multi.finance.entity.Customer;
import com.multi.finance.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        return customerRepository.findAllByOrderByNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getActive() {
        return customerRepository.findByActiveTrueOrderByNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest req) {
        String name = req.getName().trim().toUpperCase();
        if (customerRepository.existsByNameIgnoreCase(name)) {
            throw new RuntimeException("Customer already exists: " + name);
        }
        Customer c = Customer.builder()
                .name(name)
                .phone(req.getPhone())
                .area(req.getArea() != null ? req.getArea().trim().toUpperCase() : null)
                .tier(req.getTier())
                .shopType(req.getShopType())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        return toResponse(customerRepository.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest req) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        String name = req.getName().trim().toUpperCase();
        if (!c.getName().equalsIgnoreCase(name) && customerRepository.existsByNameIgnoreCase(name)) {
            throw new RuntimeException("Customer already exists: " + name);
        }
        c.setName(name);
        if (req.getPhone()    != null) c.setPhone(req.getPhone());
        if (req.getArea()     != null) c.setArea(req.getArea().trim().toUpperCase());
        if (req.getTier()     != null) c.setTier(req.getTier());
        if (req.getShopType() != null) c.setShopType(req.getShopType());
        return toResponse(customerRepository.save(c));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        c.setActive(active);
        customerRepository.save(c);
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.deleteById(id);
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .area(c.getArea())
                .customerCode(c.getCustomerCode())
                .address(c.getAddress())
                .tier(c.getTier())
                .shopType(c.getShopType())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
