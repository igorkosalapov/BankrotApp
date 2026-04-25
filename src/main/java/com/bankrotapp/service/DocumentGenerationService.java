package com.bankrotapp.service;

import com.bankrotapp.document.DocxTemplateRenderer;
import com.bankrotapp.model.Address;
import com.bankrotapp.model.BankAccount;
import com.bankrotapp.model.BankruptcyApplicationData;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.model.RealEstateItem;
import com.bankrotapp.model.Vehicle;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DocumentGenerationService {
    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DASH = "-";
    private static final String TEMPLATE_APPENDIX_1 = "templates/prilozhenie_1.docx";
    private static final String TEMPLATE_APPENDIX_2 = "templates/prilozhenie_2.docx";
    private static final List<String> TEMPLATE_ARTIFACTS_FOR_IVANOV = List.of(
            "Захаров",
            "ВЭББАНКИР",
            "ТУРБОЗАЙМ",
            "МИГКРЕДИТ",
            "MITSUBISHI RVR",
            "1 248 887,93"
    );

    private final DebtCalculationService debtCalculationService;
    private final DocxTemplateRenderer docxTemplateRenderer;

    public DocumentGenerationService(DebtCalculationService debtCalculationService,
                                     DocxTemplateRenderer docxTemplateRenderer) {
        this.debtCalculationService = debtCalculationService;
        this.docxTemplateRenderer = docxTemplateRenderer;
    }

    public byte[] generateZip(BankruptcyApplicationData data) throws IOException {
        Debtor debtor = data.debtor();
        List<Creditor> creditors = data.creditors();
        List<RealEstateItem> realEstateItems = data.propertyInfo().realEstateItems();
        List<Vehicle> vehicles = data.propertyInfo().vehicles();
        List<BankAccount> bankAccounts = List.of();

        byte[] statementDocx = generateStatementDocx(data);
        byte[] appendixOneDocx = generateAppendixOneDocx(data, creditors);
        byte[] appendixTwoDocx = generateAppendixTwoDocx(data, realEstateItems, vehicles, bankAccounts);

        if ("Иванов Сергей Николаевич".equals(debtor.fullName())) {
            warnIfTemplateArtifacts("Заявление", statementDocx);
            warnIfTemplateArtifacts("Приложение №1", appendixOneDocx);
            warnIfTemplateArtifacts("Приложение №2", appendixTwoDocx);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            String fio = sanitizeFileName(debtor.fullName());
            addZipEntry(zipOutputStream, "Заявление_о_банкротстве_" + fio + ".docx", statementDocx);
            addZipEntry(zipOutputStream, "Приложение_1_Список_кредиторов_" + fio + ".docx", appendixOneDocx);
            addZipEntry(zipOutputStream, "Приложение_2_Опись_имущества_" + fio + ".docx", appendixTwoDocx);
            zipOutputStream.finish();
            return out.toByteArray();
        }
    }

    public byte[] generateAppendixOneDocx(BankruptcyApplicationData data, List<Creditor> creditors) throws IOException {
        byte[] rendered = renderTemplate(TEMPLATE_APPENDIX_1, placeholders(data));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            requireTableIndex(document, 1, "Приложение №1");
            fillDebtorInfo(document, data.debtor());
            fillCreditorsTable(document, creditors);
            scrubLegacyTemplateArtifacts(document, data.debtor());
            document.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateAppendixTwoDocx(BankruptcyApplicationData data,
                                          List<RealEstateItem> realEstateItems,
                                          List<Vehicle> vehicles,
                                          List<BankAccount> bankAccounts) throws IOException {
        byte[] rendered = renderTemplate(TEMPLATE_APPENDIX_2, placeholders(data));
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            requireTableIndex(document, 3, "Приложение №2");
            fillDebtorInfo(document, data.debtor());
            fillRealEstateTable(document.getTables().get(1), realEstateItems);
            fillVehicleTable(document.getTables().get(2), vehicles, data.debtor().registrationAddress());
            fillBankAccountsTable(document.getTables().get(3), bankAccounts);
            scrubLegacyTemplateArtifacts(document, data.debtor());
            document.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateStatementDocx(BankruptcyApplicationData data) throws IOException {
        Debtor debtor = data.debtor();
        List<Creditor> creditors = data.creditors() == null ? List.of() : data.creditors();
        String totalDebt = debtCalculationService.formatAmountRu(debtCalculationService.calculateTotalDebt(creditors));

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            appendParagraph(document, "ЗАЯВЛЕНИЕ о признании гражданина банкротом", true);
            appendParagraph(document, "Должник: " + safe(debtor.fullName()), false);
            appendParagraph(document, "Дата рождения: "
                    + (debtor.birthDate() == null ? DASH : debtor.birthDate().format(DATE_FORMATTER)), false);
            appendParagraph(document, "Место рождения: " + safe(debtor.birthPlace()), false);
            appendParagraph(document, "Адрес регистрации: " + formatAddress(debtor.registrationAddress()), false);
            appendParagraph(document, "СНИЛС: " + safe(debtor.snils()), false);
            appendParagraph(document, "ИНН: " + safe(debtor.inn()), false);
            appendParagraph(document, "Паспорт: " + safe(debtor.passportNumber()), false);
            appendParagraph(document, "Кредиторы:", true);
            if (creditors.isEmpty()) {
                appendParagraph(document, "Кредиторы отсутствуют.", false);
            } else {
                for (Creditor creditor : creditors) {
                    appendParagraph(document, "- " + safe(creditor.name()), false);
                }
            }
            appendParagraph(document, "Общая сумма задолженности: " + totalDebt, true);
            appendParagraph(document, "Приложения: Приложение №1; Приложение №2.", false);
            appendParagraph(document, "Подпись: " + safe(debtor.fullName()), false);

            document.write(out);
            return out.toByteArray();
        }
    }

    private void appendParagraph(XWPFDocument document, String text, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setText(text);
    }

    private byte[] renderTemplate(String templatePath, Map<String, String> placeholders) throws IOException {
        ClassPathResource template = new ClassPathResource(templatePath);
        try (InputStream inputStream = template.getInputStream()) {
            return docxTemplateRenderer.render(inputStream, placeholders);
        }
    }

    private Map<String, String> placeholders(BankruptcyApplicationData data) {
        Debtor debtor = data.debtor();
        Map<String, String> map = new LinkedHashMap<>();
        String[] fioParts = splitFio(debtor.fullName());
        map.put("debtor.fullName", safe(debtor.fullName()));
        map.put("debtor.lastName", fioParts[0]);
        map.put("debtor.firstName", fioParts[1]);
        map.put("debtor.middleName", fioParts[2]);
        map.put("debtor.shortName", fioParts[0] + " "
                + (DASH.equals(fioParts[1]) ? "" : fioParts[1].charAt(0) + ". ")
                + (DASH.equals(fioParts[2]) ? "" : fioParts[2].charAt(0) + "."));
        map.put("debtor.birthDate", debtor.birthDate() == null ? DASH : debtor.birthDate().format(DATE_FORMATTER));
        map.put("debtor.birthPlace", safe(debtor.birthPlace()));
        map.put("debtor.inn", safe(debtor.inn()));
        map.put("debtor.snils", safe(debtor.snils()));
        map.put("debtor.passportNumber", safe(debtor.passportNumber()));
        map.put("debtor.registrationAddress.region", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().region()));
        map.put("debtor.registrationAddress.city", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().city()));
        map.put("debtor.registrationAddress.street", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().street()));
        map.put("debtor.registrationAddress.postalCode", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().postalCode()));
        map.put("debtor.registrationAddress.fullAddress", formatAddress(debtor.registrationAddress()));
        map.put("vehicle.primaryLabel", DASH);
        map.put("creditor.sample1", DASH);
        map.put("creditor.sample2", DASH);
        map.put("creditor.sample3", DASH);
        map.put("creditorsBlock", creditorsBlock(data.creditors()));
        map.put("realEstateBlock", propertyPresenceBlock(data.propertyInfo().realEstateItems(), "отсутствует"));
        map.put("vehicleBlock", propertyPresenceBlock(data.propertyInfo().vehicles(), "отсутствует"));
        map.put("familyBlock", familyBlock(data));
        map.put("employmentBlock", employmentBlock(data));
        map.put("attachmentsBlock", "Приложение №1; Приложение №2.");
        map.put("signatureFullName", safe(debtor.fullName()));
        map.put("totalDebtFormatted", debtCalculationService.formatAmountRu(debtCalculationService.calculateTotalDebt(data.creditors())));
        return map;
    }

    private String creditorsBlock(List<Creditor> creditors) {
        if (creditors == null || creditors.isEmpty()) {
            return "Кредиторы отсутствуют.";
        }
        return creditors.stream()
                .map(Creditor::name)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Кредиторы отсутствуют.");
    }

    private String propertyPresenceBlock(List<?> items, String emptyText) {
        return (items == null || items.isEmpty()) ? emptyText : "имеется";
    }

    private String familyBlock(BankruptcyApplicationData data) {
        if (data.familyInfo() == null) {
            return DASH;
        }
        String marriage = data.familyInfo().married() ? "в браке" : "брак расторгнут";
        String children = data.familyInfo().children().isEmpty() ? "Дети: отсутствуют." : "Дети: имеются.";
        return marriage + ". " + children;
    }

    private String employmentBlock(BankruptcyApplicationData data) {
        if (data.employmentInfo() == null) {
            return DASH;
        }
        return "UNEMPLOYED".equalsIgnoreCase(data.employmentInfo().employmentStatus())
                ? "официально не трудоустроен"
                : safe(data.employmentInfo().employerName());
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String fileName, byte[] fileBytes) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        zipOutputStream.write(fileBytes);
        zipOutputStream.closeEntry();
    }

    private String sanitizeFileName(String value) {
        return safe(value).replaceAll("[\\\\/:*?\"<>|]", "_").replace(' ', '_');
    }

    private void fillDebtorInfo(XWPFDocument document, Debtor debtor) {
        XWPFTable debtorTable = document.getTables().get(0);
        requireRowIndex(debtorTable, 20, "Блок сведений о гражданине");
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
        requireRowIndex(table, 4, "Таблица кредиторов");
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
                        debtCalculationService.formatAmountRu(totalDebtByContract(contract)),
                        debtCalculationService.formatAmountRu(principalAndInterest(contract)),
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
        requireRowIndex(table, 10, "Таблица недвижимости");
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
        requireRowIndex(table, 15, "Таблица транспортных средств");
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
        String baseLabel = safe(row.getCell(1).getText());
        String prefix = baseLabel.contains(")") ? baseLabel.substring(0, baseLabel.indexOf(')') + 1) : "1)";
        if (vehicle == null) {
            setCellText(row.getCell(1), prefix);
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

        setCellText(row.getCell(1), prefix + " " + vehicleLabel);
        setCellText(row.getCell(2), safe(vehicle.registrationNumber()));
        setCellText(row.getCell(3), "Собственность");
        setCellText(row.getCell(4), formatAddress(storageAddress));
        setCellText(row.getCell(5), DASH);
        setCellText(row.getCell(6), DASH);
    }

    private void fillBankAccountsTable(XWPFTable table, List<BankAccount> bankAccounts) {
        requireRowIndex(table, 6, "Таблица банковских счетов");
        for (int rowIndex = 2; rowIndex <= 6; rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            BankAccount account = rowIndex - 2 < bankAccounts.size() ? bankAccounts.get(rowIndex - 2) : null;

            setCellText(row.getCell(1), account == null ? DASH : safe(account.bankNameAndAddress()));
            setCellText(row.getCell(2), account == null ? DASH : safe(account.accountTypeAndCurrency()));
            setCellText(row.getCell(3), account == null || account.openDate() == null ? DASH : account.openDate().format(DATE_FORMATTER));
            setCellText(row.getCell(4), account == null ? DASH : safe(account.balanceRubles()));
        }
    }

    private BigDecimal totalDebtByContract(Contract contract) {
        if (contract == null) {
            return BigDecimal.ZERO;
        }
        return safeMoney(contract.principalDebt())
                .add(safeMoney(contract.interestDebt()))
                .add(safeMoney(contract.penalties()));
    }

    private BigDecimal principalAndInterest(Contract contract) {
        if (contract == null) {
            return BigDecimal.ZERO;
        }
        return safeMoney(contract.principalDebt())
                .add(safeMoney(contract.interestDebt()));
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void requireTableIndex(XWPFDocument document, int requiredIndex, String templateName) {
        if (document.getTables().size() <= requiredIndex) {
            throw new IllegalStateException(templateName + ": ожидается таблица с индексом " + requiredIndex + ", фактически " + document.getTables().size());
        }
    }

    private void requireRowIndex(XWPFTable table, int requiredIndex, String blockName) {
        if (table.getNumberOfRows() <= requiredIndex) {
            throw new IllegalStateException(blockName + ": ожидается строка с индексом " + requiredIndex + ", фактически " + table.getNumberOfRows());
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

    private void warnIfTemplateArtifacts(String documentName, byte[] docxBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            List<String> foundMarkers = new ArrayList<>();
            for (String marker : TEMPLATE_ARTIFACTS_FOR_IVANOV) {
                if (text.contains(marker)) {
                    foundMarkers.add(marker);
                }
            }
            if (!foundMarkers.isEmpty()) {
                log.warn("{}: в результирующем DOCX найдены legacy-маркеры: {}", documentName, foundMarkers);
            }
        } catch (IOException exception) {
            log.warn("{}: не удалось выполнить проверку legacy-маркеров: {}", documentName, exception.getMessage());
        }
    }

    private void scrubLegacyTemplateArtifacts(XWPFDocument document, Debtor debtor) {
        String[] fioParts = splitFio(debtor.fullName());
        String fullName = safe(debtor.fullName());
        String lastName = fioParts[0];
        replaceLegacyMarkersInParagraphs(document.getParagraphs(), fullName, lastName);
        replaceLegacyMarkersInTables(document.getTables(), fullName, lastName);

        for (XWPFHeader header : document.getHeaderList()) {
            replaceLegacyMarkersInParagraphs(header.getParagraphs(), fullName, lastName);
            replaceLegacyMarkersInTables(header.getTables(), fullName, lastName);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            replaceLegacyMarkersInParagraphs(footer.getParagraphs(), fullName, lastName);
            replaceLegacyMarkersInTables(footer.getTables(), fullName, lastName);
        }
    }

    private void replaceLegacyMarkersInTables(List<XWPFTable> tables, String fullName, String lastName) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceLegacyMarkersInParagraphs(cell.getParagraphs(), fullName, lastName);
                    replaceLegacyMarkersInTables(cell.getTables(), fullName, lastName);
                }
            }
        }
    }

    private void replaceLegacyMarkersInParagraphs(List<XWPFParagraph> paragraphs, String fullName, String lastName) {
        for (XWPFParagraph paragraph : paragraphs) {
            String source = paragraph.getText();
            if (source == null || source.isBlank()) {
                continue;
            }

            String updated = source
                    .replace("Захаров Владимир Игоревич", fullName)
                    .replace("Захаров В. И.", shortName(fullName))
                    .replace("Захаров", lastName);

            if (source.equals(updated)) {
                continue;
            }

            int runCount = paragraph.getRuns().size();
            for (int i = runCount - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            paragraph.createRun().setText(updated);
        }
    }

    private String shortName(String fullName) {
        String[] fioParts = splitFio(fullName);
        if (DASH.equals(fioParts[0])) {
            return DASH;
        }
        String firstInitial = DASH.equals(fioParts[1]) ? "" : fioParts[1].charAt(0) + ".";
        String middleInitial = DASH.equals(fioParts[2]) ? "" : " " + fioParts[2].charAt(0) + ".";
        return fioParts[0] + " " + firstInitial + middleInitial;
    }

}
