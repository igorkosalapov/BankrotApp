package com.bankrotapp.model;

public record Vehicle(
        String type,
        String brand,
        String model,
        String registrationNumber,
        Integer year
) {
}
