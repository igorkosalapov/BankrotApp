package com.bankrotapp.model;

import java.math.BigDecimal;

public record Contract(
        String contractNumber,
        String contractType,
        BigDecimal principalDebt,
        BigDecimal interestDebt,
        BigDecimal penalties
) {
}
