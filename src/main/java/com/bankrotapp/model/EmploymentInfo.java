package com.bankrotapp.model;

import java.math.BigDecimal;

public record EmploymentInfo(
        String employmentStatus,
        String employerName,
        String position,
        BigDecimal monthlyIncome
) {
}
