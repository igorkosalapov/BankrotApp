package com.bankrotapp.controller;

import com.bankrotapp.model.Address;
import com.bankrotapp.model.Child;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.RealEstateItem;
import com.bankrotapp.model.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PreviewInputParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public List<Creditor> parseCreditors(String creditorLines) {
        if (creditorLines == null || creditorLines.isBlank()) {
            return List.of();
        }

        record Key(String name, String address) {}
        Map<Key, List<Contract>> grouped = new LinkedHashMap<>();

        for (String line : creditorLines.split("\\R")) {
            String[] row = splitRow(line, 8);
            if (row == null) {
                row = splitRow(line, 3);
            }
            if (row == null) {
                continue;
            }

            String creditorName = row[0];
            if (creditorName.isBlank()) {
                continue;
            }

            String creditorAddress = row.length >= 8 ? row[1] : "";
            String contractType = row.length >= 8 ? normalizeContractType(row[2]) : "loan";
            String contractNumber = row.length >= 8 ? row[3] : row[1];
            String contractDate = row.length >= 8 ? row[4] : "";
            BigDecimal amount = parseAmount(row.length >= 8 ? row[5] : row[2]);

            String basis = contractNumber;
            if (!contractDate.isBlank()) {
                basis = basis + " от " + contractDate;
            }

            grouped.computeIfAbsent(new Key(creditorName, creditorAddress), key -> new ArrayList<>())
                    .add(new Contract(basis, contractType, amount, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        List<Creditor> result = new ArrayList<>();
        for (Map.Entry<Key, List<Contract>> entry : grouped.entrySet()) {
            result.add(new Creditor(entry.getKey().name(), entry.getKey().address(), entry.getValue()));
        }
        return result;
    }

    public List<RealEstateItem> parseRealEstateItems(String lines) {
        List<String[]> rows = parseStructuredLines(lines, 7);
        List<RealEstateItem> items = new ArrayList<>();
        for (String[] row : rows) {
            Double area = parseDouble(row[3]);
            items.add(new RealEstateItem(
                    row[0],
                    new Address("Россия", "", "", row[1], "", "", ""),
                    area,
                    row[2].isBlank() ? "Собственность" : row[2]
            ));
        }
        return items;
    }

    public List<Vehicle> parseVehicles(String lines) {
        List<String[]> rows = parseStructuredLines(lines, 11);
        List<Vehicle> vehicles = new ArrayList<>();
        for (String[] row : rows) {
            vehicles.add(new Vehicle(row[0], row[1], row[2], row[3], parseInteger(row[4])));
        }
        return vehicles;
    }

    public List<Child> parseChildren(String lines) {
        List<String[]> rows = parseStructuredLines(lines, 3);
        List<Child> children = new ArrayList<>();
        for (String[] row : rows) {
            children.add(new Child(row[0], parseDate(row[1])));
        }
        return children;
    }

    public List<String[]> parseStructuredLines(String lines, int columns) {
        if (lines == null || lines.isBlank()) {
            return List.of();
        }
        List<String[]> parsed = new ArrayList<>();
        for (String line : lines.split("\\R")) {
            String[] row = splitRow(line, columns);
            if (row != null) {
                parsed.add(row);
            }
        }
        return parsed;
    }

    private String[] splitRow(String line, int columns) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.trim().split("\\|", -1);
        if (parts.length < columns) {
            return null;
        }
        String[] normalized = new String[columns];
        for (int i = 0; i < columns; i++) {
            normalized[i] = parts[i].trim();
        }
        return normalized;
    }

    private String normalizeContractType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "ЗАЙМ", "MICROLOAN" -> "microloan";
            case "КРЕДИТНАЯ_КАРТА", "CARD", "CREDIT_CARD" -> "card";
            default -> "loan";
        };
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInteger(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
