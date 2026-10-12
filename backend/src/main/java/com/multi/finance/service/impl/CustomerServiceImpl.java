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
                .phone2(blankToNull(req.getPhone2()))
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
        if (req.getPhone2()   != null) c.setPhone2(blankToNull(req.getPhone2()));
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
                .phone2(c.getPhone2())
                .area(c.getArea())
                .customerCode(c.getCustomerCode())
                .address(c.getAddress())
                .tier(c.getTier())
                .shopType(c.getShopType())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .build();
    }

    /** Applies only the customer identity/contact fields permitted through edit requests. */
    @Transactional
    public CustomerResponse applyRequestedChanges(Long id, java.util.Map<String, Object> changes) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        java.util.Set<String> allowed = java.util.Set.of("name", "area", "phone", "phone2");
        if (!allowed.containsAll(changes.keySet())) {
            throw new RuntimeException("Customer edit requests may change only name, area, and mobile numbers.");
        }
        if (changes.isEmpty()) throw new RuntimeException("The customer request has no changes.");

        if (changes.containsKey("name")) {
            String name = stringValue(changes.get("name")).trim().toUpperCase();
            if (name.isBlank()) throw new RuntimeException("Customer name is required.");
            if (!c.getName().equalsIgnoreCase(name) && customerRepository.existsByNameIgnoreCase(name)) {
                throw new RuntimeException("Customer already exists: " + name);
            }
            c.setName(name);
        }
        if (changes.containsKey("area")) {
            String area = stringValue(changes.get("area")).trim();
            if (area.isBlank()) throw new RuntimeException("Customer area is required.");
            c.setArea(area.toUpperCase());
        }
        if (changes.containsKey("phone")) {
            String phone = stringValue(changes.get("phone")).trim();
            if (phone.isBlank()) throw new RuntimeException("Primary mobile number is required.");
            c.setPhone(phone);
        }
        if (changes.containsKey("phone2")) {
            c.setPhone2(blankToNull(changes.get("phone2") == null
                    ? null : stringValue(changes.get("phone2"))));
        }
        return toResponse(customerRepository.save(c));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
