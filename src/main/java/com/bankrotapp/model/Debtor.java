package com.bankrotapp.model;

import java.time.LocalDate;

public record Debtor(
        String fullName,
        LocalDate birthDate,
        String snils,
        String inn,
        String passportNumber,
        Address registrationAddress,
        Address actualAddress,
        String phone,
        String email
) {
}
