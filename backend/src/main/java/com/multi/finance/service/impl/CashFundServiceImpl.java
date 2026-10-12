package com.multi.finance.service.impl;

import com.multi.finance.dto.request.CashFundRequest;
import com.multi.finance.dto.response.CashBalanceResponse;
import com.multi.finance.dto.response.CashFundResponse;
import com.multi.finance.entity.CashFund;
import com.multi.finance.entity.User;
import com.multi.finance.repository.CashFundRepository;
import com.multi.finance.repository.ExpenseRepository;
import com.multi.finance.repository.PettyCashDeductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CashFundServiceImpl {

    private final CashFundRepository cashFundRepository;
    private final ExpenseRepository expenseRepository;
    private final PettyCashDeductionRepository pettyCashDeductionRepository;
    private final com.multi.finance.repository.ExpenseAdvanceRepository expenseAdvanceRepository;

    @Transactional
    public CashFundResponse addFund(CashFundRequest req) {
        CashFund fund = CashFund.builder()
                .amount(req.getAmount())
                .date(req.getDate())
                .description(req.getDescription())
                .addedBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();
        return toResponse(cashFundRepository.save(fund));
    }

    @Transactional(readOnly = true)
    public CashBalanceResponse getBalance() {
        BigDecimal totalFunded     = cashFundRepository.totalFunded();
        BigDecimal expensesSpent   = expenseRepository.totalSpent();
        BigDecimal deductionsSpent = pettyCashDeductionRepository.totalDeducted();
        BigDecimal totalSpent      = expensesSpent.add(deductionsSpent);
        BigDecimal outstandingAdvances = expenseAdvanceRepository.totalOutstanding();
        return CashBalanceResponse.builder()
                .totalFunded(totalFunded)
                .totalSpent(totalSpent)
                .outstandingAdvances(outstandingAdvances)
                .balance(totalFunded.subtract(totalSpent).subtract(outstandingAdvances))
                .build();
    }

    @Transactional(readOnly = true)
    public List<CashFundResponse> getHistory() {
        return cashFundRepository.findAllByOrderByDateDescCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private CashFundResponse toResponse(CashFund f) {
        return CashFundResponse.builder()
                .id(f.getId())
                .amount(f.getAmount())
                .date(f.getDate())
                .description(f.getDescription())
                .addedByName(f.getAddedBy().getFullName())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
