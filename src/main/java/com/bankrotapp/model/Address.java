package com.bankrotapp.model;

public record Address(
        String country,
        String region,
        String city,
        String street,
        String house,
        String apartment,
        String postalCode
) {
}
