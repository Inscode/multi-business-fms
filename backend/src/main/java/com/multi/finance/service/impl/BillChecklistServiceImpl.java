package com.multi.finance.service.impl;

import com.multi.finance.dto.request.BillChecklistClosingRequest;
import com.multi.finance.dto.request.BillChecklistNoteRequest;
import com.multi.finance.dto.response.BillChecklistDashboardResponse;
import com.multi.finance.dto.response.BillChecklistNoteResponse;
import com.multi.finance.dto.response.BillChecklistVerifyRowResponse;
import com.multi.finance.entity.BillChecklistClosing;
import com.multi.finance.entity.BillChecklistNote;
import com.multi.finance.entity.User;
import com.multi.finance.repository.BillChecklistClosingRepository;
import com.multi.finance.repository.BillChecklistNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillChecklistServiceImpl {

    private final BillChecklistNoteRepository noteRepository;
    private final BillChecklistClosingRepository closingRepository;

    // ── Notes ──────────────────────────────────────────────────────────

    @Transactional
    public BillChecklistNoteResponse addNote(BillChecklistNoteRequest req) {
        LocalDate date = req.getEntryDate() != null ? req.getEntryDate() : LocalDate.now();
        BillChecklistNote note = BillChecklistNote.builder()
                .itemName(req.getItemName().trim())
                .count(req.getCount())
                .note(req.getNote())
                .entryDate(date)
                .createdBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();
        return toNoteResponse(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<BillChecklistNoteResponse> getNotesForDate(LocalDate date) {
        return noteRepository.findByEntryDateOrderByCreatedAtAsc(date)
                .stream().map(this::toNoteResponse).toList();
    }

    @Transactional
    public void deleteNote(Long id) {
        noteRepository.deleteById(id);
    }

    // ── Verify view ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillChecklistVerifyRowResponse> getVerifyView(LocalDate date) {
        // Sum of notes per item ON this date
        Map<String, Integer> todayMap = toIntMap(noteRepository.sumByItemForDate(date));

        // Sum of notes per item BEFORE this date
        Map<String, Integer> historyNotesMap = toIntMap(noteRepository.sumByItemBeforeDate(date));

        // Sum of entered per item BEFORE this date
        Map<String, Integer> historyEnteredMap = toIntMap(closingRepository.sumEnteredByItemBeforeDate(date));

        // All unique items: today's notes + items with carry forward > 0
        Set<String> items = new LinkedHashSet<>(todayMap.keySet());
        historyNotesMap.forEach((item, total) -> {
            int entered = historyEnteredMap.getOrDefault(item, 0);
            if (total > entered) items.add(item); // carry forward > 0
        });

        if (items.isEmpty()) return List.of();

        // Today's closings
        Map<String, Integer> todayClosingMap = closingRepository
                .findByClosingDateOrderByItemNameAsc(date)
                .stream()
                .collect(Collectors.toMap(
                        BillChecklistClosing::getItemName,
                        BillChecklistClosing::getMarkedEntered));

        return items.stream().sorted().map(item -> {
            int histNotes   = historyNotesMap.getOrDefault(item, 0);
            int histEntered = historyEnteredMap.getOrDefault(item, 0);
            int carryForward = Math.max(0, histNotes - histEntered);

            int todayNotes   = todayMap.getOrDefault(item, 0);
            int total        = carryForward + todayNotes;
            int markedEntered = todayClosingMap.getOrDefault(item, 0);
            int balance      = total - markedEntered;

            LocalDate carryFromDate = carryForward > 0
                    ? noteRepository.findEarliestDateBeforeDate(item, date)
                    : null;

            return BillChecklistVerifyRowResponse.builder()
                    .itemName(item)
                    .carryForward(carryForward)
                    .carryFromDate(carryFromDate)
                    .todayNotes(todayNotes)
                    .total(total)
                    .markedEntered(markedEntered)
                    .balance(balance)
                    .build();
        }).toList();
    }

    // ── Closing / save ─────────────────────────────────────────────────

    @Transactional
    public void saveClosing(BillChecklistClosingRequest req) {
        LocalDate date = req.getClosingDate() != null ? req.getClosingDate() : LocalDate.now();
        User user = getCurrentUser();

        for (BillChecklistClosingRequest.ClosingItem item : req.getItems()) {
            if (item.getMarkedEntered() < 0) continue;

            BillChecklistClosing closing = closingRepository
                    .findByItemNameAndClosingDate(item.getItemName(), date)
                    .orElse(BillChecklistClosing.builder()
                            .itemName(item.getItemName())
                            .closingDate(date)
                            .build());

            closing.setMarkedEntered(item.getMarkedEntered());
            closing.setClosedBy(user);
            closing.setClosedAt(LocalDateTime.now());
            closingRepository.save(closing);
        }
    }

    // ── Dashboard summary ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillChecklistDashboardResponse getDashboardSummary() {
        LocalDate today = LocalDate.now();

        // Total noted today
        int totalNotedToday = noteRepository.sumByItemForDate(today).stream()
                .mapToInt(row -> ((Number) row[1]).intValue())
                .sum();

        // Total entered today (from closing)
        int totalEnteredToday = closingRepository.totalEnteredForDate(today);

        // Compute verify rows to find items with balance
        List<BillChecklistVerifyRowResponse> rows = getVerifyView(today);
        int itemsWithBalance = (int) rows.stream().filter(r -> r.getBalance() > 0).count();
        int pendingBalance   = rows.stream().mapToInt(BillChecklistVerifyRowResponse::getBalance).sum();

        return BillChecklistDashboardResponse.builder()
                .totalNotedToday(totalNotedToday)
                .totalEnteredToday(totalEnteredToday)
                .pendingBalance(pendingBalance)
                .itemsWithBalance(itemsWithBalance)
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, Integer> toIntMap(List<Object[]> rows) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private BillChecklistNoteResponse toNoteResponse(BillChecklistNote n) {
        return BillChecklistNoteResponse.builder()
                .id(n.getId())
                .itemName(n.getItemName())
                .count(n.getCount())
                .note(n.getNote())
                .entryDate(n.getEntryDate())
                .createdByName(n.getCreatedBy().getFullName())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
