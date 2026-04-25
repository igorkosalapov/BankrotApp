package com.bankrotapp.model;

import java.util.List;

public record PropertyInfo(
        List<Vehicle> vehicles,
        List<RealEstateItem> realEstateItems,
        boolean hasOtherValuableProperty
) {
}
