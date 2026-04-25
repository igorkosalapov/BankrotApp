package com.bankrotapp.model;

import java.time.LocalDate;

public record Child(
        String fullName,
        LocalDate birthDate
) {
}
