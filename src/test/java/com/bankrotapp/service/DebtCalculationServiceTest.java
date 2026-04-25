package com.bankrotapp.service;

import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebtCalculationServiceTest {

    private final DebtCalculationService debtCalculationService = new DebtCalculationService();

    @Test
    void shouldCalculateSumByCreditor() {
        Creditor creditor = new Creditor(
                "Bank A",
                "1234567890",
                List.of(
                        new Contract("1", "loan", new BigDecimal("10000.00"), new BigDecimal("500.00"), new BigDecimal("100.00")),
                        new Contract("2", "credit card", new BigDecimal("3000.00"), new BigDecimal("200.00"), new BigDecimal("50.00"))
                )
        );

        BigDecimal total = debtCalculationService.calculateCreditorTotal(creditor);

        assertEquals(new BigDecimal("13850.00"), total);
    }

    @Test
    void shouldCalculateTotalDebt() {
        Creditor first = new Creditor(
                "Bank A",
                "123",
                List.of(new Contract("1", "loan", new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("10.00")))
        );

        Creditor second = new Creditor(
                "MFO B",
                "456",
                List.of(new Contract("2", "microloan", new BigDecimal("500.00"), new BigDecimal("50.00"), new BigDecimal("5.00")))
        );

        BigDecimal total = debtCalculationService.calculateTotalDebt(List.of(first, second));

        assertEquals(new BigDecimal("1665.00"), total);
    }

    @Test
    void shouldFormatAmountInRussianLocale() {
        String formatted = debtCalculationService.formatAmountRu(new BigDecimal("1234567.8"));

        assertEquals("1\u00A0234\u00A0567,80", formatted);
    }
}
