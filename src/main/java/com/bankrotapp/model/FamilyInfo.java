package com.bankrotapp.model;

import java.util.List;

public record FamilyInfo(
        boolean married,
        String spouseName,
        List<Child> children
) {
}
