package com.bankrotapp.controller;

import com.bankrotapp.model.Address;
import com.bankrotapp.model.BankAccount;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.model.RealEstateItem;
import com.bankrotapp.model.Vehicle;
import com.bankrotapp.service.DebtCalculationService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
public class DocumentGenerationController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DASH = "-";

    private final DebtCalculationService debtCalculationService;

    public DocumentGenerationController(DebtCalculationService debtCalculationService) {
        this.debtCalculationService = debtCalculationService;
    }

    @PostMapping(value = "/generate", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generateStatement() throws IOException {
        Debtor debtor = new Debtor(
                "Иванов Иван Иванович",
                LocalDate.of(1989, 3, 14),
                "112-233-445 95",
                "770123456789",
                "4510 123456",
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                "79161234567",
                "ivanov@example.com",
                "г. Москва"
        );

        List<Creditor> creditors = List.of(
                new Creditor(
                        "Банк А",
                        "7700000000",
                        List.of(new Contract("Кредитный договор № 1 от 10.01.2024", "loan", new BigDecimal("150000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                ),
                new Creditor(
                        "МФО Б",
                        "7800000000",
                        List.of(new Contract("Договор займа № 77 от 03.02.2025", "microloan", new BigDecimal("25000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                )
        );

        ClassPathResource template = new ClassPathResource("templates/Prilozhenie_1.docx");
        byte[] generated;

        try (InputStream inputStream = template.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            fillDebtorInfo(document, debtor);
            fillCreditorsTable(document, creditors);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                generated = out.toByteArray();
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-1.docx").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(generated);
    }

    @PostMapping(value = "/generate/appendix-2", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generatePropertyInventory() throws IOException {
        Debtor debtor = new Debtor(
                "Иванов Иван Иванович",
                LocalDate.of(1989, 3, 14),
                "112-233-445 95",
                "770123456789",
                "4510 123456",
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                "79161234567",
                "ivanov@example.com",
                "г. Москва"
        );

        List<RealEstateItem> realEstateItems = List.of(
                new RealEstateItem("квартира", debtor.registrationAddress(), 62.4, "Собственность")
        );
        List<Vehicle> vehicles = List.of(
                new Vehicle("легковой автомобиль", "Hyundai", "Solaris", "X7LBR32AAB1234567", 2017)
        );
        List<BankAccount> bankAccounts = List.of();

        ClassPathResource template = new ClassPathResource("templates/Prilozhenie_2.docx");
        byte[] generated;

        try (InputStream inputStream = template.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            fillDebtorInfo(document, debtor);
            fillRealEstateTable(document.getTables().get(1), realEstateItems);
            fillVehicleTable(document.getTables().get(2), vehicles, debtor.registrationAddress());
            fillBankAccountsTable(document.getTables().get(3), bankAccounts);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                generated = out.toByteArray();
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-2.docx").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(generated);
    }

    private void fillDebtorInfo(XWPFDocument document, Debtor debtor) {
        XWPFTable debtorTable = document.getTables().get(0);
        String[] fioParts = splitFio(debtor.fullName());

        setCellText(debtorTable.getRow(1).getCell(2), fioParts[0]);
        setCellText(debtorTable.getRow(2).getCell(2), fioParts[1]);
        setCellText(debtorTable.getRow(3).getCell(2), fioParts[2]);
        setCellText(debtorTable.getRow(4).getCell(2), DASH);
        setCellText(debtorTable.getRow(5).getCell(2), debtor.birthDate().format(DATE_FORMATTER));
        setCellText(debtorTable.getRow(6).getCell(2), debtor.birthPlace());
        setCellText(debtorTable.getRow(7).getCell(2), debtor.snils());
        setCellText(debtorTable.getRow(8).getCell(2), debtor.inn());
        setCellText(debtorTable.getRow(10).getCell(2), "паспорт");
        setCellText(debtorTable.getRow(11).getCell(2), debtor.passportNumber());

        Address address = debtor.registrationAddress();
        setCellText(debtorTable.getRow(13).getCell(2), safe(address.region()));
        setCellText(debtorTable.getRow(14).getCell(2), DASH);
        setCellText(debtorTable.getRow(15).getCell(2), safe(address.city()));
        setCellText(debtorTable.getRow(16).getCell(2), DASH);
        setCellText(debtorTable.getRow(17).getCell(2), safe(address.street()));
        setCellText(debtorTable.getRow(18).getCell(2), safe(address.house()));
        setCellText(debtorTable.getRow(19).getCell(2), DASH);
        setCellText(debtorTable.getRow(20).getCell(2), safe(address.apartment()));
    }

    private void fillCreditorsTable(XWPFDocument document, List<Creditor> creditors) {
        XWPFTable table = document.getTables().get(1);
        List<CreditorContractRow> rows = flattenContracts(creditors);

        for (int rowIdx = 4; rowIdx < table.getNumberOfRows(); rowIdx++) {
            XWPFTableRow row = table.getRow(rowIdx);
            if (row.getTableCells().size() < 8) {
                continue;
            }

            int dataIndex = rowIdx - 4;
            CreditorContractRow data = dataIndex < rows.size() ? rows.get(dataIndex) : null;

            setCellText(row.getCell(0), "1." + (dataIndex + 1));
            setCellText(row.getCell(1), data == null ? DASH : data.obligationContent());
            setCellText(row.getCell(2), data == null ? DASH : data.creditorName());
            setCellText(row.getCell(3), data == null ? DASH : data.creditorAddress());
            setCellText(row.getCell(4), data == null ? DASH : data.basis());
            setCellText(row.getCell(5), data == null ? DASH : data.totalAmount());
            setCellText(row.getCell(6), data == null ? DASH : data.debtAmount());
            setCellText(row.getCell(7), data == null ? DASH : data.penalties());
        }
    }

    private List<CreditorContractRow> flattenContracts(List<Creditor> creditors) {
        List<CreditorContractRow> rows = new ArrayList<>();
        for (Creditor creditor : creditors) {
            for (Contract contract : creditor.contracts()) {
                String penalties = hasValue(contract.penalties()) ? debtCalculationService.formatAmountRu(contract.penalties()) : DASH;
                String obligationContent = switch (contract.contractType()) {
                    case "microloan" -> "договор займа";
                    case "loan" -> "кредитный договор";
                    default -> "денежное обязательство";
                };

                rows.add(new CreditorContractRow(
                        obligationContent,
                        creditor.name(),
                        creditorAddress(creditor.name()),
                        contract.contractNumber(),
                        debtCalculationService.formatAmountRu(contract.principalDebt()),
                        debtCalculationService.formatAmountRu(contract.principalDebt()),
                        penalties
                ));
            }
        }
        return rows;
    }

    private String creditorAddress(String creditorName) {
        return switch (creditorName) {
            case "Банк А" -> "125009, г. Москва, ул. Охотный Ряд, д. 1";
            case "МФО Б" -> "191186, г. Санкт-Петербург, Невский пр-т, д. 15";
            default -> DASH;
        };
    }

    private boolean hasValue(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private void setCellText(XWPFTableCell cell, String value) {
        String text = value == null || value.isBlank() ? DASH : value;
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            cell.removeParagraph(i);
        }
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }

    private String[] splitFio(String fullName) {
        String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        String surname = parts.length > 0 ? parts[0] : DASH;
        String name = parts.length > 1 ? parts[1] : DASH;
        String patronymic = parts.length > 2 ? parts[2] : DASH;
        return new String[]{surname, name, patronymic};
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? DASH : value;
    }

    private void fillRealEstateTable(XWPFTable table, List<RealEstateItem> items) {
        fillRealEstateCategoryRow(table, 2, "земел", items);
        fillRealEstateCategoryRow(table, 4, "дом", items, "дач");
        fillRealEstateCategoryRow(table, 6, "квартир", items);
        fillRealEstateCategoryRow(table, 7, "гараж", items);
        fillRealEstateCategoryRow(table, 9, "ин", items);

        clearRowToDashes(table.getRow(3), 1);
        clearRowToDashes(table.getRow(5), 1);
        clearRowToDashes(table.getRow(8), 1);
        clearRowToDashes(table.getRow(10), 1);
    }

    private void fillRealEstateCategoryRow(XWPFTable table, int rowIndex, String keyword, List<RealEstateItem> items, String... extraKeywords) {
        RealEstateItem item = items.stream()
                .filter(candidate -> matchesCategory(candidate.type(), keyword, extraKeywords))
                .findFirst()
                .orElse(null);

        XWPFTableRow row = table.getRow(rowIndex);
        if (item == null) {
            for (int col = 2; col <= 6; col++) {
                setCellText(row.getCell(col), DASH);
            }
            return;
        }

        setCellText(row.getCell(2), safe(item.ownershipType()));
        setCellText(row.getCell(3), formatAddress(item.address()));
        setCellText(row.getCell(4), item.areaSquareMeters() == null ? DASH : formatDecimal(item.areaSquareMeters()));
        setCellText(row.getCell(5), "Правоустанавливающие документы");
        setCellText(row.getCell(6), DASH);
    }

    private void fillVehicleTable(XWPFTable table, List<Vehicle> vehicles, Address storageAddress) {
        fillVehicleCategoryRow(table, 2, "легк", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 4, "груз", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 6, "мото", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 8, "сельск", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 10, "водн", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 12, "возд", vehicles, storageAddress);
        fillVehicleCategoryRow(table, 14, "ин", vehicles, storageAddress);

        clearRowToDashes(table.getRow(3), 1);
        clearRowToDashes(table.getRow(5), 1);
        clearRowToDashes(table.getRow(7), 1);
        clearRowToDashes(table.getRow(9), 1);
        clearRowToDashes(table.getRow(11), 1);
        clearRowToDashes(table.getRow(13), 1);
        clearRowToDashes(table.getRow(15), 1);
    }

    private void fillVehicleCategoryRow(XWPFTable table, int rowIndex, String keyword, List<Vehicle> vehicles, Address storageAddress) {
        Vehicle vehicle = vehicles.stream()
                .filter(candidate -> matchesCategory(candidate.type(), keyword))
                .findFirst()
                .orElse(null);

        XWPFTableRow row = table.getRow(rowIndex);
        if (vehicle == null) {
            for (int col = 2; col <= 6; col++) {
                setCellText(row.getCell(col), DASH);
            }
            return;
        }

        String vehicleLabel = List.of(safe(vehicle.brand()), safe(vehicle.model()), safe(vehicle.year() == null ? null : String.valueOf(vehicle.year())))
                .stream()
                .filter(value -> !DASH.equals(value))
                .reduce((left, right) -> left + " " + right)
                .orElse(DASH);

        setCellText(row.getCell(1), row.getCell(1).getText().replace("1)", "1) " + vehicleLabel));
        setCellText(row.getCell(2), safe(vehicle.registrationNumber()));
        setCellText(row.getCell(3), "Собственность");
        setCellText(row.getCell(4), formatAddress(storageAddress));
        setCellText(row.getCell(5), DASH);
        setCellText(row.getCell(6), DASH);
    }

    private void fillBankAccountsTable(XWPFTable table, List<BankAccount> bankAccounts) {
        for (int rowIndex = 2; rowIndex <= 6; rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            BankAccount account = rowIndex - 2 < bankAccounts.size() ? bankAccounts.get(rowIndex - 2) : null;

            setCellText(row.getCell(1), account == null ? DASH : safe(account.bankNameAndAddress()));
            setCellText(row.getCell(2), account == null ? DASH : safe(account.accountTypeAndCurrency()));
            setCellText(row.getCell(3), account == null || account.openDate() == null ? DASH : account.openDate().format(DATE_FORMATTER));
            setCellText(row.getCell(4), account == null ? DASH : safe(account.balanceRubles()));
        }
    }

    private String formatAddress(Address address) {
        if (address == null) {
            return DASH;
        }

        List<String> chunks = new ArrayList<>();
        chunks.add(address.postalCode());
        chunks.add(address.region());
        chunks.add(address.city());
        chunks.add(address.street());
        chunks.add(address.house() == null || address.house().isBlank() ? null : "д. " + address.house());
        chunks.add(address.apartment() == null || address.apartment().isBlank() ? null : "кв. " + address.apartment());

        return chunks.stream()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse(DASH);
    }

    private String formatDecimal(Double value) {
        return value == null ? DASH : String.format(Locale.ROOT, "%.1f", value);
    }

    private void clearRowToDashes(XWPFTableRow row, int startCell) {
        if (row == null) {
            return;
        }
        for (int cellIndex = startCell; cellIndex < row.getTableCells().size(); cellIndex++) {
            setCellText(row.getCell(cellIndex), DASH);
        }
    }

    private boolean matchesCategory(String value, String keyword, String... extraKeywords) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains(keyword)) {
            return true;
        }
        for (String extraKeyword : extraKeywords) {
            if (normalized.contains(extraKeyword)) {
                return true;
            }
        }
        return false;
    }

    private record CreditorContractRow(
            String obligationContent,
            String creditorName,
            String creditorAddress,
            String basis,
            String totalAmount,
            String debtAmount,
            String penalties
    ) {
    }
}
