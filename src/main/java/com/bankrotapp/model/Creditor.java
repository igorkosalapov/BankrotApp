package com.bankrotapp.model;

import java.util.List;

public record Creditor(
        String name,
        String inn,
        List<Contract> contracts
) {
}
