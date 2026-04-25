package com.bankrotapp.model;

import java.time.LocalDate;

public record BankAccount(
        String bankNameAndAddress,
        String accountTypeAndCurrency,
        LocalDate openDate,
        String balanceRubles
) {
}
