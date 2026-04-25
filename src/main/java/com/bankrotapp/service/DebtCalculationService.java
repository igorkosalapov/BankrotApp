package com.bankrotapp.service;

import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class DebtCalculationService {

    public BigDecimal calculateCreditorTotal(Creditor creditor) {
        if (creditor == null || creditor.contracts() == null) {
            return BigDecimal.ZERO;
        }

        return creditor.contracts().stream()
                .filter(Objects::nonNull)
                .map(this::calculateContractTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalDebt(List<Creditor> creditors) {
        if (creditors == null) {
            return BigDecimal.ZERO;
        }

        return creditors.stream()
                .filter(Objects::nonNull)
                .map(this::calculateCreditorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String formatAmountRu(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("ru", "RU"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);

        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private BigDecimal calculateContractTotal(Contract contract) {
        return safe(contract.principalDebt())
                .add(safe(contract.interestDebt()))
                .add(safe(contract.penalties()));
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
