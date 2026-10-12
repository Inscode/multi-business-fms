package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ExpenseBusFareRequest;
import com.multi.finance.entity.ExpenseBusFare;
import com.multi.finance.repository.ExpenseBusFareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class ExpenseBusFareService {
    private final ExpenseBusFareRepository repository;

    @Transactional(readOnly = true)
    public List<ExpenseBusFare> active() { return repository.findAll().stream().sorted(java.util.Comparator.comparing(ExpenseBusFare::getFromLocation).thenComparing(ExpenseBusFare::getToLocation)).toList(); }

    @Transactional
    public ExpenseBusFare create(ExpenseBusFareRequest request) {
        String from = clean(request.getFromLocation());
        String to = clean(request.getToLocation());
        if (from.isBlank() || to.isBlank() || from.equalsIgnoreCase(to))
            throw new IllegalArgumentException("Enter two different locations.");
        if (request.getAmount() == null || request.getAmount().signum() <= 0)
            throw new IllegalArgumentException("Fare must be greater than zero.");
        if (repository.existsByFromLocationIgnoreCaseAndToLocationIgnoreCase(from, to))
            throw new IllegalArgumentException("This fare route already exists.");
        return repository.save(ExpenseBusFare.builder().fromLocation(from).toLocation(to)
                .amount(request.getAmount()).active(true).createdAt(LocalDateTime.now()).build());
    }

    @Transactional
    public ExpenseBusFare active(Long id, boolean active) {
        ExpenseBusFare fare = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Fare route not found."));
        fare.setActive(active);
        return repository.save(fare);
    }

    @Transactional
    public ExpenseBusFare update(Long id, ExpenseBusFareRequest request) {
        ExpenseBusFare fare = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Fare route not found."));
        String from = clean(request.getFromLocation());
        String to = clean(request.getToLocation());
        if (from.isBlank() || to.isBlank() || from.equalsIgnoreCase(to)) throw new IllegalArgumentException("Enter two different locations.");
        if (request.getAmount() == null || request.getAmount().signum() <= 0) throw new IllegalArgumentException("Fare must be greater than zero.");
        if (repository.existsByFromLocationIgnoreCaseAndToLocationIgnoreCaseAndIdNot(from, to, id))
            throw new IllegalArgumentException("This fare route already exists.");
        fare.setFromLocation(from); fare.setToLocation(to); fare.setAmount(request.getAmount());
        return repository.save(fare);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
}
