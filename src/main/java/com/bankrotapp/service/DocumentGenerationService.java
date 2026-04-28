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
import com.bankrotapp.template.TemplatePreparationTool;
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
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class DocumentGenerationService {
    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String DASH = "-";
    private static final String TEMPLATE_APPENDIX_1 = "templates/prilozhenie_1.docx";
    private static final String TEMPLATE_APPENDIX_2 = "templates/prilozhenie_2.docx";
    private static final String TEMPLATE_STATEMENT = "templates/zayavlenie.docx";
    private static final Set<String> DOCX_COMMENT_ENTRIES = Set.of(
            "word/comments.xml",
            "word/commentsExtended.xml",
            "word/commentsIds.xml",
            "word/people.xml"
    );
    private static final List<String> REQUIRED_STATEMENT_MARKERS = List.of(
            "{{headerBlock}}",
            "{{debtorIntroBlock}}",
            "{{creditorsDebtBlock}}",
            "{{familyBlock}}",
            "{{vehicleBlock}}",
            "{{attachmentsBlock}}",
            "{{signatureFullName}}"
    );
    private static final List<String> TEMPLATE_ARTIFACTS_FOR_IVANOV = List.of(
            "Захаров",
            "ВЭББАНКИР",
            "ТУРБОЗАЙМ",
            "МИГКРЕДИТ",
            "MITSUBISHI RVR",
            "1 248 887,93",
            "Наймушина",
            "Захарова Алёна",
            "75 10 742228",
            "744713194008",
            "113-764-260-43"
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
            addDebtorFullNameLine(document, data.debtor().fullName());
            document.write(out);
            return scrubLegacyRawXml(out.toByteArray(), data.debtor());
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
            addDebtorFullNameLine(document, data.debtor().fullName());
            document.write(out);
            return scrubLegacyRawXml(out.toByteArray(), data.debtor());
        }
    }

    public byte[] generateStatementDocx(BankruptcyApplicationData data) throws IOException {
        List<Creditor> creditors = data.creditors() == null ? List.of() : data.creditors();
        String totalDebt = debtCalculationService.formatAmountRu(debtCalculationService.calculateTotalDebt(creditors));
        byte[] templateBytes;
        try (InputStream templateStream = new ClassPathResource(TEMPLATE_STATEMENT).getInputStream()) {
            templateBytes = templateStream.readAllBytes();
        }
        byte[] preparedTemplateBytes = prepareStatementTemplate(templateBytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(preparedTemplateBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            replaceStatementDynamicSections(document, data, creditors, totalDebt);
            replaceStatementMarkers(document, data, creditors, totalDebt);
            scrubLegacyTemplateArtifacts(document, data.debtor());
            document.write(out);
            byte[] withoutComments = removeWordComments(out.toByteArray());
            return scrubLegacyRawXml(withoutComments, data.debtor());
        }
    }


    private void replaceStatementMarkers(XWPFDocument document,
                                         BankruptcyApplicationData data,
                                         List<Creditor> creditors,
                                         String totalDebt) {
        Debtor debtor = data.debtor();
        replaceStatementBlock(document, "{{headerBlock}}", statementHeaderBlock(data, creditors));
        replaceStatementBlock(document, "{{debtorIntroBlock}}", debtorIntroBlock(debtor, totalDebt));
        replaceStatementBlock(document, "{{creditorsDebtBlock}}", String.join("\n", creditorsDebtBlockLines(creditors, totalDebt)));
        replaceStatementBlock(document, "{{realEstateBlock}}", realEstateStatementBlock(data.propertyInfo().realEstateItems()));
        replaceStatementBlock(document, "{{vehicleBlock}}", vehicleStatementBlock(data.propertyInfo().vehicles()));
        replaceStatementBlock(document, "{{familyBlock}}", familyBlock(data));
        replaceStatementBlock(document, "{{employmentBlock}}", employmentBlock(data));
        replaceStatementBlock(document, "{{loanPurposeBlock}}", loanPurposeBlock(data));
        replaceStatementBlock(document, "{{financialHardshipBlock}}", financialHardshipBlock(data));
        replaceStatementBlock(document, "{{courtRequestsBlock}}", courtRequestsBlock(debtor));
        replaceStatementBlock(document, "{{attachmentsBlock}}", String.join("\n", attachmentsStatementLines(data, creditors)));
        replaceStatementBlock(document, "{{signatureFullName}}", safe(debtor.fullName()));
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
        map.put("debtor.fullNameGenitive", fullNameGenitive(debtor.fullName()));
        map.put("debtor.lastName", fioParts[0]);
        map.put("debtor.firstName", fioParts[1]);
        map.put("debtor.middleName", fioParts[2]);
        map.put("debtor.shortName", fioParts[0] + " "
                + (DASH.equals(fioParts[1]) ? "" : fioParts[1].charAt(0) + ". ")
                + (DASH.equals(fioParts[2]) ? "" : fioParts[2].charAt(0) + "."));
        map.put("debtor.birthDate", debtor.birthDate() == null ? DASH : debtor.birthDate().format(DATE_FORMATTER));
        map.put("debtor.birthDateText", debtor.birthDate() == null ? DASH : debtor.birthDate().format(DATE_FORMATTER));
        map.put("debtor.birthPlace", safe(debtor.birthPlace()));
        map.put("debtor.inn", safe(debtor.inn()));
        map.put("debtor.snils", safe(debtor.snils()));
        map.put("debtor.passportNumber", safe(debtor.passportNumber()));
        map.put("debtor.passportFull", safe(debtor.passportNumber()));
        map.put("debtor.registrationAddress.region", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().region()));
        map.put("debtor.registrationAddress.city", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().city()));
        map.put("debtor.registrationAddress.street", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().street()));
        map.put("debtor.registrationAddress.postalCode", debtor.registrationAddress() == null ? DASH : safe(debtor.registrationAddress().postalCode()));
        map.put("debtor.registrationAddress.fullAddress", formatAddress(debtor.registrationAddress()));
        map.put("vehicle.primaryLabel", DASH);
        map.put("creditor.sample1", DASH);
        map.put("creditor.sample2", DASH);
        map.put("creditor.sample3", DASH);
        map.put("creditorsBlock", creditorsDebtBlock(data.creditors(), debtCalculationService.formatAmountRu(debtCalculationService.calculateTotalDebt(data.creditors()))));
        map.put("creditorsHeaderBlock", creditorsHeaderBlock(data.creditors()));
        map.put("creditorsDebtBlock", creditorsDebtBlock(data.creditors(), debtCalculationService.formatAmountRu(debtCalculationService.calculateTotalDebt(data.creditors()))));
        map.put("realEstateBlock", propertyPresenceBlock(data.propertyInfo().realEstateItems(), "отсутствует"));
        map.put("vehicleBlock", propertyPresenceBlock(data.propertyInfo().vehicles(), "отсутствует"));
        map.put("familyBlock", familyBlock(data));
        map.put("employmentBlock", employmentBlock(data));
        map.put("loanPurposeBlock", loanPurposeBlock(data));
        map.put("financialHardshipBlock", financialHardshipBlock(data));
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

    private String creditorsHeaderBlock(List<Creditor> creditors) {
        if (creditors == null || creditors.isEmpty()) {
            return "Кредиторы отсутствуют.";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(3, creditors.size()); i++) {
            lines.add("Кредитор " + (i + 1) + ": " + safe(creditors.get(i).name()));
        }
        return String.join("\n", lines);
    }

    private String creditorsDebtBlock(List<Creditor> creditors, String totalDebt) {
        if (creditors == null || creditors.isEmpty()) {
            return "Денежные обязательства отсутствуют.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Данные обязательства возникли по следующим основаниям:");
        int rowNumber = 1;
        for (Creditor creditor : creditors) {
            List<Contract> contracts = creditor.contracts() == null ? List.of() : creditor.contracts();
            String creditorTotal = debtCalculationService.formatAmountRu(debtCalculationService.calculateCreditorTotal(creditor));
            lines.add(rowNumber + ". Перед кредитором " + safe(creditor.name()) + " общая сумма задолженности в размере " + creditorTotal + " руб.:");

            for (Contract contract : contracts) {
                lines.add("- на основании " + contractBasis(contract) + " — "
                        + debtCalculationService.formatAmountRu(totalDebtByContract(contract)) + " руб.");
            }
            rowNumber++;
        }
        lines.add("Общий размер обязательств Должника перед кредиторами составляет " + totalDebt + " руб.");
        return String.join("\n", lines);
    }

    private String propertyPresenceBlock(List<?> items, String emptyText) {
        return (items == null || items.isEmpty()) ? emptyText : "имеется";
    }

    private String familyBlock(BankruptcyApplicationData data) {
        if (data.familyInfo() == null) {
            return DASH;
        }
        String marriage;
        if (data.familyInfo().married()) {
            marriage = "В браке состоит.";
        } else if (!safe(data.familyInfo().spouseName()).equals(DASH)) {
            marriage = "Брак расторгнут, " + trimTrailingDot(safe(data.familyInfo().spouseName())) + ".";
        } else {
            marriage = "В браке не состоит.";
        }
        String children = data.familyInfo().children().isEmpty()
                ? "Несовершеннолетних детей на иждивении не имеет."
                : "Несовершеннолетние дети на иждивении имеются.";
        return marriage + " " + children;
    }

    private String employmentBlock(BankruptcyApplicationData data) {
        if (data.employmentInfo() == null) {
            return DASH;
        }
        return "UNEMPLOYED".equalsIgnoreCase(data.employmentInfo().employmentStatus())
                ? "Официального места работы не имеет."
                : safe(data.employmentInfo().employerName());
    }

    private String loanPurposeBlock(BankruptcyApplicationData data) {
        return "Кредитные средства использованы на личные и бытовые нужды должника.";
    }

    private String financialHardshipBlock(BankruptcyApplicationData data) {
        return "Ухудшение финансового положения связано с совокупным ростом долговой нагрузки и отсутствием достаточного дохода.";
    }

    private String realEstateStatementBlock(List<RealEstateItem> items) {
        return items == null || items.isEmpty()
                ? "Объекты недвижимости в собственности отсутствуют."
                : "У должника имеется недвижимое имущество (перечень приведен в приложении №2).";
    }

    private String vehicleStatementBlock(List<Vehicle> vehicles) {
        return vehicles == null || vehicles.isEmpty()
                ? "Транспортные средства у должника отсутствуют."
                : "У Должника в собственности есть движимое имущество: " + vehicleLabelForStatement(vehicles.get(0)) + ".";
    }

    private String attachmentsStatementBlock(List<Creditor> creditors) {
        List<String> lines = new ArrayList<>();
        lines.add("Список кредиторов и должников гражданина (Приложение №1).");
        lines.add("Опись имущества гражданина (Приложение №2).");
        for (Creditor creditor : Optional.ofNullable(creditors).orElse(List.of())) {
            lines.add("Подтверждение задолженности перед " + safe(creditor.name()) + ".");
        }
        return String.join("\n", lines);
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

    private void addDebtorFullNameLine(XWPFDocument document, String fullName) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText("Должник: " + safe(fullName));
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

    byte[] prepareStatementTemplate(byte[] rawTemplateBytes) throws IOException {
        byte[] prepared = TemplatePreparationTool.prepareStatementTemplate(rawTemplateBytes);
        assertStatementTemplatePrepared(prepared, "Statement template preparation failed: missing marker ");
        return prepared;
    }

    void assertStatementTemplatePrepared(byte[] templateBytes, String prefix) throws IOException {
        String xml = readWordXml(templateBytes);
        for (String marker : REQUIRED_STATEMENT_MARKERS) {
            if (!xml.contains(marker)) {
                throw new IllegalStateException(prefix + marker);
            }
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

    private String fullNameGenitive(String fullName) {
        String[] fio = splitFio(fullName);
        String lastName = fio[0];
        String firstName = fio[1];
        String patronymic = fio[2];
        if (DASH.equals(lastName)) {
            return DASH;
        }
        return toGenitiveLastName(lastName) + " " + toGenitiveName(firstName) + " " + toGenitivePatronymic(patronymic);
    }

    private void replaceStatementDynamicSections(XWPFDocument document,
                                                 BankruptcyApplicationData data,
                                                 List<Creditor> creditors,
                                                 String totalDebt) {
        Debtor debtor = data.debtor();
        String debtorIntro = safe(debtor.fullName()) + " (" + safeDateText(debtor) + " года рождения в "
                + safe(debtor.birthPlace()) + ", паспорт: " + safe(debtor.passportNumber())
                + ", зарегистрирован: " + formatAddress(debtor.registrationAddress())
                + ", ИНН: " + safe(debtor.inn()) + "; СНИЛС: " + safe(debtor.snils()) + ")";

        replaceParagraphStartingWith(document, "Заявитель (должник):", "Заявитель (должник): " + safe(debtor.fullName()));
        replaceParagraphStartingWith(document, "454014,", formatAddress(debtor.registrationAddress()));
        replaceParagraphStartingWith(document, "Кредитор1:", "Кредитор1: " + creditorName(creditors, 0));
        replaceParagraphStartingWith(document, "Кредитор 2:", "Кредитор 2: " + creditorName(creditors, 1));
        replaceParagraphStartingWith(document, "Кредитор 3:", "Кредитор 3: " + creditorName(creditors, 2));
        replaceParagraphStartingWith(document, "125466,", DASH);
        replaceParagraphStartingWith(document, "123290,", DASH);
        replaceParagraphStartingWith(document, "127018,", DASH);

        replaceParagraphStartingWith(document, "Захаров Владимир Игоревич (", debtorIntro + ", не является индивидуальным предпринимателем.");
        replaceParagraphStartingWith(document, "Захаров В. В.", shortName(debtor.fullName()) + " имеет не исполненные денежные обязательства в размере " + totalDebt + " рублей.");
        replaceParagraphRange(
                document,
                "Данные обязательства возникли по следующим основаниям:",
                "Общий размер обязательств Должника перед кредиторами составляет",
                creditorsDebtBlockLines(creditors, totalDebt)
        );
        replaceParagraphStartingWith(document, "У Должника в собственности отсутствует недвижимое имущество", realEstateStatementBlock(data.propertyInfo().realEstateItems()));
        replaceParagraphStartingWith(document, "У Должника в собственности есть движимое имущество:", vehicleStatementBlock(data.propertyInfo().vehicles()));
        replaceParagraphStartingWith(document, "Согласно свидетельству о заключении брака", familyBlock(data));
        replaceParagraphRange(
                document,
                "в 2022 году доход",
                "В трудном денежном положении",
                employmentAndHardshipLines(data)
        );
        replaceParagraphStartingWith(document, "1.  Признать гражданина", "1.  Признать гражданина  "
                + fullNameGenitive(debtor.fullName()) + " (" + safeDateText(debtor)
                + " года рождения, ИНН: " + safe(debtor.inn()) + "; СНИЛС: " + safe(debtor.snils())
                + "), несостоятельным (банкротом), ввести процедуру реализации имущества гражданина.");
        replaceParagraphStartingWith(document, "3.  Рассмотреть заявление о признании гражданина", "3.  Рассмотреть заявление о признании гражданина "
                + fullNameGenitive(debtor.fullName()) + " несостоятельным (банкротом) в отсутствие заявителя.");
        replaceParagraphRange(document,
                "Список кредиторов и должников гражданина",
                "Доказательство отправки копии заявления кредиторам",
                attachmentsStatementLines(data, creditors));
        replaceParagraphStartingWith(document, "_________________________/", "_________________________/ " + safe(debtor.fullName()));
    }

    private void replaceStatementBlock(XWPFDocument document, String marker, String blockText) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        replaceTextEverywhere(document, marker, blockText);
    }

    private void replaceTextEverywhere(XWPFDocument document, String from, String to) {
        replaceInParagraphs(document.getParagraphs(), from, to);
        replaceInTablesText(document.getTables(), from, to);
        for (XWPFHeader header : document.getHeaderList()) {
            replaceInParagraphs(header.getParagraphs(), from, to);
            replaceInTablesText(header.getTables(), from, to);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            replaceInParagraphs(footer.getParagraphs(), from, to);
            replaceInTablesText(footer.getTables(), from, to);
        }
    }

    private void replaceInTablesText(List<XWPFTable> tables, String from, String to) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceInParagraphs(cell.getParagraphs(), from, to);
                    replaceInTablesText(cell.getTables(), from, to);
                }
            }
        }
    }

    private void replaceInParagraphs(List<XWPFParagraph> paragraphs, String from, String to) {
        if (from == null || from.isBlank()) {
            return;
        }
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (text == null || text.isEmpty() || !text.contains(from)) {
                continue;
            }
            String updated = text.replace(from, to == null ? "" : to);
            int runCount = paragraph.getRuns().size();
            for (int i = runCount - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            paragraph.createRun().setText(updated);
        }
    }

    private String safeDateText(Debtor debtor) {
        return debtor.birthDate() == null ? DASH : debtor.birthDate().format(DATE_FORMATTER);
    }

    private String creditorName(List<Creditor> creditors, int index) {
        return index < creditors.size() ? safe(creditors.get(index).name()) : DASH;
    }

    private List<String> creditorsDebtBlockLines(List<Creditor> creditors, String totalDebt) {
        List<String> lines = new ArrayList<>();
        lines.add("Данные обязательства возникли по следующим основаниям:");
        int rowNumber = 1;
        for (Creditor creditor : creditors) {
            String creditorTotal = debtCalculationService.formatAmountRu(debtCalculationService.calculateCreditorTotal(creditor));
            lines.add(rowNumber + ". Перед кредитором " + safe(creditor.name()) + " общая сумма задолженности в размере " + creditorTotal + " руб.");
            List<Contract> contracts = Optional.ofNullable(creditor.contracts()).orElse(List.of());
            for (Contract contract : contracts) {
                lines.add("- " + contractBasis(contract)
                        + " — " + debtCalculationService.formatAmountRu(totalDebtByContract(contract)) + " руб.");
            }
            rowNumber++;
        }
        lines.add("Общий размер обязательств Должника перед кредиторами составляет " + totalDebt + " руб.");
        return lines;
    }

    private List<String> employmentAndHardshipLines(BankruptcyApplicationData data) {
        List<String> lines = new ArrayList<>();
        lines.add(employmentBlock(data));
        lines.add(loanPurposeBlock(data));
        lines.add(financialHardshipBlock(data));
        return lines;
    }

    private List<String> attachmentsStatementLines(BankruptcyApplicationData data, List<Creditor> creditors) {
        List<String> lines = new ArrayList<>();
        lines.add("Список кредиторов и должников гражданина (Приложение №1) – 4 листах;");
        lines.add("Опись имущества гражданина (Приложение №2) – 2 листа;");
        for (Creditor creditor : creditors) {
            lines.add("Подтверждение задолженности перед " + safe(creditor.name()) + " – __ лист;");
        }
        lines.add("Копия ИНН – 1 лист;");
        lines.add("Копия страхового свидетельства государственного пенсионного страхования (СНИЛС) – 1 лист;");
        lines.add("Копия паспорта гражданина РФ – 5 листах;");
        String gibddLine = data.propertyInfo().vehicles() == null || data.propertyInfo().vehicles().isEmpty()
                ? "Ответ из ГИБДД об отсутствии транспортных средств – 1лист;"
                : "Ответ из ГИБДД о наличии транспортных средств – 1лист;";
        lines.add(gibddLine);
        lines.add("Уведомление из ЕГРН об отсутствии недвижимого имущества – 1 лист;");
        lines.add("Справка о том, что должник не является индивидуальным предпринимателем – 1 лист;");
        lines.add("Сведения о состоянии индивидуального лицевого счета застрахованного лица – 4 листах;");
        lines.add("Сведения об открытых банковских счетах – 1 лист;");
        lines.add("Сведения об отсутствии задолженности по налогам – 1 лист;");
        lines.add("Справка о наличии (отсутствии) судимости и (или) факта уголовного преследования либо о прекращении уголовного преследования  – 1 лист;");
        lines.add("оригинал чек-ордера внесения денежных средств на вознаграждение Арбитражному управляющему – 1 лист;");
        lines.add("Доказательство отправки копии заявления кредиторам – 5 листах.");
        return lines;
    }

    private void replaceParagraphStartingWith(XWPFDocument document, String prefix, String replacement) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();
            if (text != null && text.startsWith(prefix)) {
                clearAndSetParagraph(paragraph, replacement);
                return;
            }
        }
    }

    private void replaceParagraphRange(XWPFDocument document, String startPrefix, String endPrefix, List<String> lines) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        int start = -1;
        int end = -1;
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i).getText();
            if (text == null) {
                continue;
            }
            if (start < 0 && text.startsWith(startPrefix)) {
                start = i;
            }
            if (text.startsWith(endPrefix)) {
                end = i;
            }
        }
        if (start < 0 || end < start) {
            return;
        }
        int lineCount = Math.min(lines.size(), end - start + 1);
        for (int i = 0; i < lineCount; i++) {
            clearAndSetParagraph(paragraphs.get(start + i), lines.get(i));
        }
        for (int i = start + lineCount; i <= end; i++) {
            clearAndSetParagraph(paragraphs.get(i), "");
        }
    }

    private void clearAndSetParagraph(XWPFParagraph paragraph, String text) {
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        paragraph.createRun().setText(text == null ? "" : text);
    }

    private String contractLabel(String contractType) {
        if ("microloan".equalsIgnoreCase(contractType)) {
            return "Договор займа";
        }
        if ("card".equalsIgnoreCase(contractType) || "credit_card".equalsIgnoreCase(contractType)) {
            return "Договор кредитной карты";
        }
        return "Кредитный договор";
    }

    private String contractBasis(Contract contract) {
        String rawNumber = safe(contract.contractNumber());
        String normalized = rawNumber.toLowerCase(Locale.ROOT);
        if (normalized.contains("договор")) {
            return rawNumber;
        }
        return contractLabel(contract.contractType()) + " №" + rawNumber;
    }

    private String statementHeaderBlock(BankruptcyApplicationData data, List<Creditor> creditors) {
        Debtor debtor = data.debtor();
        List<String> lines = new ArrayList<>();
        lines.add("В Арбитражный суд Челябинской области");
        lines.add("454091, г. Челябинск, ул. Воровского, д.2.");
        lines.add("");
        lines.add("Заявитель (должник): " + safe(debtor.fullName()));
        lines.add(formatAddress(debtor.registrationAddress()));
        lines.add("");
        lines.add("Кредитор1: " + creditorName(creditors, 0));
        lines.add("Кредитор 2: " + creditorName(creditors, 1));
        lines.add("Кредитор 3: " + creditorName(creditors, 2));
        return String.join("\n", lines);
    }

    private String debtorIntroBlock(Debtor debtor, String totalDebt) {
        return safe(debtor.fullName()) + " (" + safeDateText(debtor) + " года рождения в " + safe(debtor.birthPlace())
                + ", паспорт: " + safe(debtor.passportNumber()) + ", зарегистрирован: " + formatAddress(debtor.registrationAddress())
                + ", ИНН: " + safe(debtor.inn()) + "; СНИЛС: " + safe(debtor.snils())
                + "), не является индивидуальным предпринимателем.\n"
                + shortName(debtor.fullName()) + " имеет не исполненные денежные обязательства в размере " + totalDebt + " рублей.";
    }

    private String courtRequestsBlock(Debtor debtor) {
        return "1.  Признать гражданина  " + fullNameGenitive(debtor.fullName()) + " (" + safeDateText(debtor)
                + " года рождения, ИНН: " + safe(debtor.inn()) + "; СНИЛС: " + safe(debtor.snils())
                + "), несостоятельным (банкротом), ввести процедуру реализации имущества гражданина.\n"
                + "2. Утвердить финансового управляющего из числа членов «СРО АУ «Южный Урал» - Ассоциация «Саморегулируемая организация арбитражных управляющих «Южный Урал».\n"
                + "3.  Рассмотреть заявление о признании гражданина " + fullNameGenitive(debtor.fullName()) + " несостоятельным (банкротом) в отсутствие заявителя.";
    }

    private String trimTrailingDot(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\.+$", "");
    }

    private byte[] scrubLegacyRawXml(byte[] docxBytes, Debtor debtor) throws IOException {
        String[] fioParts = splitFio(debtor.fullName());
        String fullName = safe(debtor.fullName());
        String shortName = shortName(fullName);
        String lastName = fioParts[0];

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(docxBytes), StandardCharsets.UTF_8);
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                byte[] content = zipInputStream.readAllBytes();
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    String xml = new String(content, StandardCharsets.UTF_8)
                            .replace("Захаров Владимир Игоревич", fullName)
                            .replace("Захаров В. И.", shortName)
                            .replace("Захаров", lastName)
                            .replace("ВЭББАНКИР", "")
                            .replace("ТУРБОЗАЙМ", "")
                            .replace("МИГКРЕДИТ", "")
                            .replace("MITSUBISHI RVR", "")
                            .replace("1 248 887,93", "")
                            .replace("Наймушина", "")
                            .replace("Захарова Алёна", "")
                            .replace("75 10 742228", "")
                            .replace("744713194008", "")
                            .replace("113-764-260-43", "");
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                zipOutputStream.write(content);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return out.toByteArray();
        }
    }

    private String readWordXml(byte[] docxBytes) throws IOException {
        StringBuilder xml = new StringBuilder();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(docxBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    xml.append(new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return xml.toString();
    }

    private byte[] removeWordComments(byte[] docxBytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(docxBytes), StandardCharsets.UTF_8);
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (DOCX_COMMENT_ENTRIES.contains(entry.getName())) {
                    continue;
                }
                byte[] content = zipInputStream.readAllBytes();
                if ("[Content_Types].xml".equals(entry.getName())
                        || "word/_rels/document.xml.rels".equals(entry.getName())) {
                    String xml = new String(content, StandardCharsets.UTF_8)
                            .replaceAll("(?s)<Override[^>]*PartName=\"/word/comments[^>]*?/>", "")
                            .replaceAll("(?s)<Relationship[^>]*Type=\"[^\"]*comments[^\"]*\"[^>]*/>", "");
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    String xml = new String(content, StandardCharsets.UTF_8)
                            .replaceAll("(?s)<w:commentRangeStart[^>]*/>", "")
                            .replaceAll("(?s)<w:commentRangeEnd[^>]*/>", "")
                            .replaceAll("(?s)<w:commentReference[^>]*/>", "");
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                zipOutputStream.write(content);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return out.toByteArray();
        }
    }

    private String vehicleLabelForStatement(Vehicle vehicle) {
        if (vehicle == null) {
            return DASH;
        }
        return safe(vehicle.brand()) + " " + safe(vehicle.model())
                + ", " + (vehicle.year() == null ? DASH : vehicle.year()) + " года выпуска, гос.№: "
                + safe(vehicle.registrationNumber());
    }

    private String toGenitiveLastName(String lastName) {
        if (lastName.endsWith("ов") || lastName.endsWith("ев") || lastName.endsWith("ин")) {
            return lastName + "а";
        }
        return lastName;
    }

    private String toGenitiveName(String firstName) {
        if (firstName == null || firstName.isBlank() || DASH.equals(firstName)) {
            return DASH;
        }
        if (firstName.endsWith("й")) {
            return firstName.substring(0, firstName.length() - 1) + "я";
        }
        if (firstName.endsWith("ь")) {
            return firstName.substring(0, firstName.length() - 1) + "я";
        }
        return firstName + "а";
    }

    private String toGenitivePatronymic(String patronymic) {
        if (patronymic == null || patronymic.isBlank() || DASH.equals(patronymic)) {
            return DASH;
        }
        if (patronymic.endsWith("ич")) {
            return patronymic + "а";
        }
        return patronymic;
    }

}
