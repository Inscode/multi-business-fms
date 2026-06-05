package com.multi.finance.service.impl;

import com.multi.finance.dto.request.BillReminderRequest;
import com.multi.finance.dto.response.BillReminderResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.BillReminder;
import com.multi.finance.entity.User;
import com.multi.finance.enums.ReminderStatus;
import com.multi.finance.repository.BillReminderRepository;
import com.multi.finance.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BillReminderServiceImpl {

    private final BillReminderRepository reminderRepository;
    private final BillRepository billRepository;

    @Transactional
    public BillReminderResponse create(BillReminderRequest request) {
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        BillReminder reminder = BillReminder.builder()
                .bill(bill)
                .reminderDate(request.getReminderDate())
                .period(request.getPeriod())
                .note(request.getNote())
                .status(ReminderStatus.PENDING)
                .createdBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(reminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public List<BillReminderResponse> getTodayAndOverdue() {
        LocalDate today = LocalDate.now();

        List<BillReminder> overdue = reminderRepository
                .findByReminderDateLessThanAndStatus(today, ReminderStatus.PENDING);
        List<BillReminder> todayList = reminderRepository
                .findByReminderDateAndStatus(today, ReminderStatus.PENDING);

        return Stream.concat(overdue.stream(), todayList.stream())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillReminderResponse> getPendingReminders() {
        return reminderRepository.findByStatus(ReminderStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BillReminderResponse> getByBill(Long billId) {
        return reminderRepository.findByBillId(billId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillReminderResponse markDone(Long id) {
        BillReminder reminder = getById(id);
        reminder.setStatus(ReminderStatus.DONE);
        return toResponse(reminderRepository.save(reminder));
    }

    @Transactional
    public BillReminderResponse cancel(Long id) {
        BillReminder reminder = getById(id);
        reminder.setStatus(ReminderStatus.CANCELLED);
        return toResponse(reminderRepository.save(reminder));
    }

    @Transactional(readOnly = true)
    public List<BillReminderResponse> getAll() {
        return reminderRepository.findAll().stream()
                .sorted((a, b) -> b.getReminderDate().compareTo(a.getReminderDate()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillReminderResponse update(Long id, BillReminderRequest request) {
        BillReminder reminder = getById(id);
        reminder.setReminderDate(request.getReminderDate());
        reminder.setPeriod(request.getPeriod());
        reminder.setNote(request.getNote());
        return toResponse(reminderRepository.save(reminder));
    }

    private BillReminder getById(Long id) {
        return reminderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));
    }

    public BillReminderResponse toResponse(BillReminder r) {
        return BillReminderResponse.builder()
                .id(r.getId())
                .billId(r.getBill().getId())
                .billNumber(r.getBill().getBillNumber())
                .customerName(r.getBill().getCustomerName())
                .area(r.getBill().getArea())
                .balanceRemaining(r.getBill().getBalanceRemaining())
                .reminderDate(r.getReminderDate())
                .period(r.getPeriod())
                .note(r.getNote())
                .status(r.getStatus())
                .createdByName(r.getCreatedBy().getFullName())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}