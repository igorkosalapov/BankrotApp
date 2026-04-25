package com.bankrotapp.model;

public record RealEstateItem(
        String type,
        Address address,
        Double areaSquareMeters,
        String ownershipType
) {
}
