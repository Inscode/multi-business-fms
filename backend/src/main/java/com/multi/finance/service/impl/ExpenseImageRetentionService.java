package com.multi.finance.service.impl;

import com.multi.finance.entity.Expense;
import com.multi.finance.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpenseImageRetentionService {

    private final ExpenseRepository expenses;
    private final ImageKitService imageKit;

    /** Remove expense receipt photos once they have been stored for 90 days. */
    @Scheduled(cron = "0 15 2 * * *")
    @Transactional
    public void deleteExpiredExpensePhotos() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        for (Expense expense : expenses.findByReceiptUploadedAtBeforeAndReceiptImageUrlIsNotNull(cutoff)) {
            if (imageKit.deleteByUrl(expense.getReceiptImageUrl())) {
                expense.setReceiptImageUrl(null);
                expense.setReceiptUploadedAt(null);
                expenses.save(expense);
            }
        }
    }
}
