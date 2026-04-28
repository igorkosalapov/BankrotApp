package com.bankrotapp.service;

import com.bankrotapp.document.DocxTemplateRenderer;
import com.bankrotapp.model.Address;
import com.bankrotapp.model.BankruptcyApplicationData;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.model.EmploymentInfo;
import com.bankrotapp.model.FamilyInfo;
import com.bankrotapp.model.PropertyInfo;
import com.bankrotapp.model.Vehicle;
import com.bankrotapp.template.TemplatePreparationTool;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentGenerationServiceTest {

    private final DocumentGenerationService service = new DocumentGenerationService(
            new DebtCalculationService(),
            new DocxTemplateRenderer()
    );
    private static final Set<String> REQUIRED_MARKERS = Set.of(
            "{{headerBlock}}",
            "{{debtorIntroBlock}}",
            "{{creditorsDebtBlock}}",
            "{{familyBlock}}",
            "{{vehicleBlock}}",
            "{{attachmentsBlock}}",
            "{{signatureFullName}}"
    );

    @Test
    void testStatementTemplateMustContainRequiredMarkers() throws Exception {
        byte[] rawStatementTemplate = readResource("templates/zayavlenie.docx");
        byte[] prepared = TemplatePreparationTool.prepareStatementTemplate(rawStatementTemplate);
        String xml = readWordXml(prepared);
        for (String marker : REQUIRED_MARKERS) {
            assertTrue(xml.contains(marker), "Отсутствует обязательный marker: " + marker);
        }
    }

    @Test
    void testGenerationFailsIfStatementTemplateIsRaw() throws Exception {
        byte[] brokenRaw = createBrokenDocxWithoutDocumentXml();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.prepareStatementTemplate(brokenRaw));
        assertTrue(exception.getMessage().contains("Statement template preparation failed: missing marker {{headerBlock}}"));
    }

    @Test
    void testGenerateZipUsesPreparedTemplatesOnly() throws Exception {
        byte[] prepared = TemplatePreparationTool.prepareStatementTemplate(readResource("templates/zayavlenie.docx"));
        String preparedXml = readWordXml(prepared);
        for (String marker : REQUIRED_MARKERS) {
            assertTrue(preparedXml.contains(marker), "Prepared template must contain marker " + marker);
        }
        byte[] zip = service.generateZip(ivanovClient());
        assertNotNull(zip);
    }

    @Test
    void testStatementKeepsLegalTemplateStructure() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertContainsWithPreview(text, "Заявление");
        assertContainsWithPreview(text, "о признании несостоятельным (банкротом) должника - физического лица");
        assertContainsWithPreview(text, "В соответствии со ст. 213.3");
        assertContainsWithPreview(text, "Согласно ст. 213.4");
        assertContainsWithPreview(text, "пункте 3 статьи 213.4");
        assertContainsWithPreview(text, "абзац второй пункта 1 статьи 42");
        assertContainsWithPreview(text, "прошу суд");
        assertContainsWithPreview(text, "Приложение документов для Арбитражного суда");
    }

    @Test
    void testStatementIsRenderedFromTemplateNotCreatedFromScratch() throws Exception {
        byte[] statementBytes = service.generateStatementDocx(ivanovClient());

        assertTrue(containsZipEntry(statementBytes, "word/styles.xml"));
        assertTrue(containsZipEntry(statementBytes, "word/numbering.xml"));
        assertTrue(containsZipEntry(statementBytes, "word/theme/theme1.xml"));
        assertTrue(containsZipEntry(statementBytes, "word/fontTable.xml"));
        assertTrue(paragraphCount(statementBytes) >= 90, "Ожидалось не менее 90 абзацев в заявлении.");
        assertTrue(runCount(statementBytes) >= 400, "Ожидалось не менее 400 runs в заявлении.");
    }

    @Test
    void testAppendix1SignatureUsesCurrentDebtor() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixOneDocx(data, data.creditors()));

        assertTrue(text.contains("Иванов Сергей Николаевич"));
        assertFalse(text.contains("Захаров"));
    }

    @Test
    void testAppendix2NoVehicleDoesNotRenderOldVehicle() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixTwoDocx(data, List.of(), List.of(), List.of()));

        assertFalse(text.contains("MITSUBISHI RVR"));
        assertTrue(text.contains("-") || text.contains("отсутств"));
    }

    @Test
    void testAppendix2SignatureUsesCurrentDebtor() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixTwoDocx(data, List.of(), List.of(), List.of()));
        assertContainsWithPreview(text, "Иванов Сергей Николаевич");
        assertNotContainsWithPreview(text, "Захаров Владимир Игоревич");
    }

    @Test
    void testGenerateZipContainsThreeDocxFiles() throws Exception {
        byte[] zip = service.generateZip(ivanovClient());

        assertNotNull(zip);
        List<String> docxEntries = listDocxEntries(zip);
        assertEquals(3, docxEntries.size());
        assertTrue(docxEntries.stream().anyMatch(name -> name.contains("Заявление_о_банкротстве_")));
        assertTrue(docxEntries.stream().anyMatch(name -> name.contains("Приложение_1_Список_кредиторов_")));
        assertTrue(docxEntries.stream().anyMatch(name -> name.contains("Приложение_2_Опись_имущества_")));
    }

    @Test
    void testStatementContainsCurrentClientData() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertContainsWithPreview(text, "Иванов Сергей Николаевич");
        assertContainsWithPreview(text, "Иванов С. Н.");
        assertContainsWithPreview(text, "Иванова Сергея Николаевича");
        assertContainsWithPreview(text, "АО Альфа-Банк");
        assertContainsWithPreview(text, "ООО МКК Срочноденьги");
        assertContainsWithPreview(text, "ООО ПКО Право Онлайн");
        assertTrue(text.contains("375 000,00") || text.contains("375\u00A0000,00"),
                "Ожидалась сумма 375 000,00. Фрагмент заявления:\n" + preview(text));

        assertNotContainsWithPreview(text, "Захаров");
        assertNotContainsWithPreview(text, "ВЭББАНКИР");
        assertNotContainsWithPreview(text, "ТУРБОЗАЙМ");
        assertNotContainsWithPreview(text, "МИГКРЕДИТ");
        assertNotContainsWithPreview(text, "MITSUBISHI RVR");
        assertNotContainsWithPreview(text, "1 248 887,93");
    }

    @Test
    void testStatementContainsCourtRequestSection() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertContainsWithPreview(text, "прошу суд");
        assertContainsWithPreview(text, "1.");
        assertContainsWithPreview(text, "2.");
        assertContainsWithPreview(text, "3.");
    }

    @Test
    void testStatementContainsAttachmentsSection() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertContainsWithPreview(text, "Приложение документов для Арбитражного суда");
        assertContainsWithPreview(text, "Приложение №1");
        assertContainsWithPreview(text, "Приложение №2");
    }

    @Test
    void testNoUnresolvedPlaceholdersInStatement() throws Exception {
        byte[] statement = service.generateStatementDocx(ivanovClient());
        String xml = readWordXml(statement);

        assertFalse(xml.contains("{{"), "В DOCX остались неразрешённые placeholders {{...}}.");
        assertFalse(xml.contains("}}"), "В DOCX остались неразрешённые placeholders {{...}}.");
    }

    @Test
    void testGeneratedZipUsesTemplatePreservingStatement() throws Exception {
        byte[] zip = service.generateZip(ivanovClient());
        byte[] statementDocx = readStatementDocx(zip);
        String text = extract(statementDocx);

        assertContainsWithPreview(text, "о признании несостоятельным (банкротом) должника - физического лица");
        assertContainsWithPreview(text, "прошу суд");
        assertContainsWithPreview(text, "Приложение документов для Арбитражного суда");

        assertContainsWithPreview(text, "Иванов Сергей Николаевич");
        assertContainsWithPreview(text, "Иванов С. Н.");
        assertContainsWithPreview(text, "Иванова Сергея Николаевича");
        assertContainsWithPreview(text, "АО Альфа-Банк");
        assertContainsWithPreview(text, "ООО МКК Срочноденьги");
        assertContainsWithPreview(text, "ООО ПКО Право Онлайн");
        assertTrue(text.contains("375 000,00") || text.contains("375\u00A0000,00"),
                "Ожидалась сумма 375 000,00 в заявлении из ZIP. Фрагмент:\n" + preview(text));

        assertNotContainsWithPreview(text, "Захаров");
        assertNotContainsWithPreview(text, "ВЭББАНКИР");
        assertNotContainsWithPreview(text, "ТУРБОЗАЙМ");
        assertNotContainsWithPreview(text, "МИГКРЕДИТ");
        assertNotContainsWithPreview(text, "MITSUBISHI RVR");
        assertNotContainsWithPreview(text, "1 248 887,93");
    }

    @Test
    void testNoUnresolvedPlaceholdersInGeneratedDocuments() throws Exception {
        byte[] zip = service.generateZip(ivanovClient());

        for (byte[] docx : readDocxEntries(zip)) {
            String xml = readWordXml(docx);
            assertFalse(xml.contains("{{"), "В DOCX остались неразрешённые placeholders {{...}}.");
            assertFalse(xml.contains("}}"), "В DOCX остались неразрешённые placeholders {{...}}.");
        }
    }

    @Test
    void testGeneratedZipHasNoLegacyDataInAnyDocx() throws Exception {
        byte[] zip = service.generateZip(ivanovClient());
        List<String> forbidden = List.of(
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
        for (byte[] docx : readDocxEntries(zip)) {
            String text = extract(docx);
            for (String marker : forbidden) {
                assertNotContainsWithPreview(text, marker);
            }
        }
    }

    @Test
    void testStatementDoesNotContainOldTemplateData() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));
        assertNotContainsWithPreview(text, "Захаров");
        assertNotContainsWithPreview(text, "ВЭББАНКИР");
        assertNotContainsWithPreview(text, "ТУРБОЗАЙМ");
        assertNotContainsWithPreview(text, "МИГКРЕДИТ");
        assertNotContainsWithPreview(text, "MITSUBISHI RVR");
        assertNotContainsWithPreview(text, "1 248 887,93");
    }



    @Test
    void testStatementUsesCorrectShortName() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "Смирнов А. П.");
        assertNotContainsWithPreview(text, "Смирнов В. В.");
        assertNotContainsWithPreview(text, "Смирнов В.И.");
        assertNotContainsWithPreview(text, "Смирнова В.И.");
    }

    @Test
    void testStatementUsesCorrectGenitiveFullName() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "Смирнова Андрея Павловича");
        assertNotContainsWithPreview(text, "Смирнова Владимира Игоревича");
    }

    @Test
    void testStatementReplacesCreditorsDebtBlockCompletely() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "ПАО ВТБ");
        assertContainsWithPreview(text, "256 000,00");
        assertContainsWithPreview(text, "VTB-2041");
        assertContainsWithPreview(text, "180 000,00");
        assertContainsWithPreview(text, "VTB-CC-87");
        assertContainsWithPreview(text, "76 000,00");
        assertContainsWithPreview(text, "ZM-5512");
        assertContainsWithPreview(text, "34 000,00");
        assertContainsWithPreview(text, "DS-908");
        assertContainsWithPreview(text, "21 500,00");
        assertNotContainsWithPreview(text, "1003074184/13");
        assertNotContainsWithPreview(text, "АА 17226041");
        assertNotContainsWithPreview(text, "1537512052");
    }

    @Test
    void testStatementFamilyBlockForDivorcedNoChildren() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));
        assertContainsWithPreview(text, "Брак расторгнут");
        assertContainsWithPreview(text, "дата расторжения брака: 22.09.2021");
        assertContainsWithPreview(text, "свидетельство о расторжении брака: II-БР №654321");
        assertContainsWithPreview(text, "Несовершеннолетних детей на иждивении не имеет");
        assertNotContainsWithPreview(text, "В браке не состоит");
        assertNotContainsWithPreview(text, "свидетельству о заключении брака");
        assertNotContainsWithPreview(text, "Захарова Алёна");
        assertNotContainsWithPreview(text, "Наймушина");
    }


    @Test
    void testStatementDoesNotDuplicateContractType() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));
        assertNotContainsWithPreview(text, "Кредитный договор №Кредитный договор");
        assertNotContainsWithPreview(text, "Кредитный договор №Договор");
        assertNotContainsWithPreview(text, "Договор займа №Договор");
    }

    @Test
    void testStatementVehicleBlockUsesCurrentVehicle() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "Lada");
        assertContainsWithPreview(text, "Granta");
        assertContainsWithPreview(text, "В321ОР174");
        assertNotContainsWithPreview(text, "MITSUBISHI RVR");
    }

    @Test
    void testAppendix2ContainsCurrentVehicleWhenVehicleExists() throws Exception {
        BankruptcyApplicationData data = smirnovClient();
        String text = extract(service.generateAppendixTwoDocx(
                data,
                data.propertyInfo().realEstateItems(),
                data.propertyInfo().vehicles(),
                List.of()
        ));
        assertContainsWithPreview(text, "Lada");
        assertContainsWithPreview(text, "Granta");
        assertContainsWithPreview(text, "В321ОР174");
        assertNotContainsWithPreview(text, "MITSUBISHI RVR");
    }

    @Test
    void testStatementAttachmentsDependOnVehiclePresence() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));
        assertContainsWithPreview(text, "Ответ из ГИБДД об отсутствии транспортных средств");
        assertNotContainsWithPreview(text, "Ответ из ГИБДД о наличии транспортных средств");
    }

    @Test
    void testGeneratedZipForSmirnovHasConsistentData() throws Exception {
        byte[] zip = service.generateZip(smirnovClient());
        List<byte[]> docs = readDocxEntries(zip);
        assertEquals(3, docs.size());
        for (byte[] doc : docs) {
            String text = extract(doc);
            assertContainsWithPreview(text, "Смирнов Андрей Павлович");
            assertNotContainsWithPreview(text, "Захаров");
            assertNotContainsWithPreview(text, "MITSUBISHI RVR");
            assertNotContainsWithPreview(text, "ВЭББАНКИР");
            assertNotContainsWithPreview(text, "ТУРБОЗАЙМ");
            assertNotContainsWithPreview(text, "МИГКРЕДИТ");
            assertNotContainsWithPreview(text, "1003074184/13");
            assertNotContainsWithPreview(text, "АА 17226041");
            assertNotContainsWithPreview(text, "1537512052");
        }
    }

    @Test
    void testStatementHeaderBlockReplacedCompletely() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "Заявитель (должник): Смирнов Андрей Павлович");
        assertContainsWithPreview(text, "Кредитор1: ПАО ВТБ");
        assertContainsWithPreview(text, "Кредитор 2: ООО МФК Займер");
        assertContainsWithPreview(text, "Кредитор 3: ООО МКК Деньги Сразу");
        assertNotContainsWithPreview(text, "Захаров Владимир Игоревич");
        assertNotContainsWithPreview(text, "ООО МФК \"ВЭББАНКИР\"");
        assertNotContainsWithPreview(text, "ООО МКК ТУРБОЗАЙМ");
        assertNotContainsWithPreview(text, "ООО \"МИГКРЕДИТ\"");
    }

    @Test
    void testDebtorIntroBlockReplacedCompletely() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertContainsWithPreview(text, "Смирнов Андрей Павлович");
        assertNotContainsWithPreview(text, "паспорт серия 75 10 742228");
        assertNotContainsWithPreview(text, "454014, Челябинская обл., Курчатовский р-н г. Челябинск, пр. Победы, д. 330, кв.44");
        assertNotContainsWithPreview(text, "744713194008");
        assertNotContainsWithPreview(text, "113-764-260-43");
    }

    @Test
    void testStatementDoesNotContainWordComments() throws Exception {
        String text = extract(service.generateStatementDocx(smirnovClient()));
        assertNotContainsWithPreview(text, "Comment by");
        assertNotContainsWithPreview(text, "Опер1");
    }

    private BankruptcyApplicationData ivanovClient() {
        Address address = new Address("Россия", "г. Москва", "Москва", "Ленинская Слобода", "19", "12", "115280");
        Debtor debtor = new Debtor(
                "Иванов Сергей Николаевич",
                LocalDate.of(1990, 5, 20),
                "123-456-789 00",
                "770812345678",
                "4511 987654",
                address,
                address,
                "79990000000",
                "ivanov-sn@example.com",
                "г. Москва"
        );

        List<Creditor> creditors = List.of(
                new Creditor("АО Альфа-Банк", "7728168971", List.of(
                        new Contract("Кредитный договор №AB-771 от 12.08.2023", "loan", new BigDecimal("210000.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new Contract("Договор кредитной карты №CC-882 от 05.09.2023", "loan", new BigDecimal("95000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                )),
                new Creditor("ООО МКК Срочноденьги", "7700000010", List.of(
                        new Contract("Договор займа №SD-14 от 10.11.2023", "microloan", new BigDecimal("27000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                )),
                new Creditor("ООО ПКО Право Онлайн", "7700000020", List.of(
                        new Contract("Договор уступки №PO-77 от 23.01.2024", "loan", new BigDecimal("43000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                ))
        );

        return new BankruptcyApplicationData(
                debtor,
                creditors,
                new FamilyInfo(false, "дата расторжения брака: 22.09.2021, свидетельство о расторжении брака: II-БР №654321", List.of()),
                new EmploymentInfo("UNEMPLOYED", "", "", BigDecimal.ZERO),
                new PropertyInfo(List.of(), List.of(), false)
        );
    }

    private BankruptcyApplicationData smirnovClient() {
        Address address = new Address("Россия", "Челябинская область", "Челябинск", "ул. Труда", "18", "7", "454091");
        Debtor debtor = new Debtor(
                "Смирнов Андрей Павлович",
                LocalDate.of(1992, 2, 10),
                "221-334-556 77",
                "744700112233",
                "7509 123456",
                address,
                address,
                "79001112233",
                "smirnov-ap@example.com",
                "г. Челябинск"
        );

        List<Creditor> creditors = List.of(
                new Creditor("ПАО ВТБ", "7702070139", List.of(
                        new Contract("VTB-2041 от 17.04.2024", "loan", new BigDecimal("180000.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new Contract("VTB-CC-87 от 03.06.2024", "card", new BigDecimal("76000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                )),
                new Creditor("ООО МФК Займер", "4205294274", List.of(
                        new Contract("ZM-5512 от 12.01.2025", "microloan", new BigDecimal("34000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                )),
                new Creditor("ООО МКК Деньги Сразу", "6453137601", List.of(
                        new Contract("DS-908 от 28.01.2025", "microloan", new BigDecimal("21500.00"), BigDecimal.ZERO, BigDecimal.ZERO)
                ))
        );

        return new BankruptcyApplicationData(
                debtor,
                creditors,
                new FamilyInfo(false, "", List.of()),
                new EmploymentInfo("UNEMPLOYED", "", "", BigDecimal.ZERO),
                new PropertyInfo(
                        List.of(new Vehicle("легковой автомобиль", "Lada", "Granta", "В321ОР174", 2019)),
                        List.of(),
                        false
                )
        );
    }

    private String extract(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private int paragraphCount(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().size();
        }
    }

    private int runCount(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return document.getParagraphs().stream().mapToInt(paragraph -> paragraph.getRuns().size()).sum();
        }
    }

    private boolean containsZipEntry(byte[] docxBytes, String entryName) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(docxBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> listDocxEntries(byte[] zipBytes) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".docx")) {
                    entries.add(entry.getName());
                }
            }
        }
        return entries;
    }

    private List<byte[]> readDocxEntries(byte[] zipBytes) throws IOException {
        List<byte[]> docxEntries = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".docx")) {
                    docxEntries.add(zipInputStream.readAllBytes());
                }
            }
        }
        return docxEntries;
    }

    private String readWordXml(byte[] docxBytes) throws IOException {
        StringBuilder xml = new StringBuilder();
        try (InputStream inputStream = new ByteArrayInputStream(docxBytes);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    xml.append(new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return xml.toString();
    }

    private byte[] readResource(String classpath) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + classpath);
            }
            return inputStream.readAllBytes();
        }
    }

    private byte[] createBrokenDocxWithoutDocumentXml() throws IOException {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(out)) {
            zipOutputStream.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
            zipOutputStream.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return out.toByteArray();
        }
    }

    private byte[] readStatementDocx(byte[] zipBytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().endsWith(".docx")
                        && entry.getName().contains("Заявление_о_банкротстве_")) {
                    return zipInputStream.readAllBytes();
                }
            }
        }
        throw new AssertionError("В ZIP не найден файл основного заявления.");
    }

    private void assertContainsWithPreview(String text, String expected) {
        String normalizedText = normalizeText(text);
        String normalizedExpected = normalizeText(expected);
        assertTrue(normalizedText.contains(normalizedExpected),
                "Ожидалось вхождение: " + expected + ". Фрагмент заявления:\n" + preview(text));
    }

    private void assertNotContainsWithPreview(String text, String unexpected) {
        String normalizedText = normalizeText(text);
        String normalizedUnexpected = normalizeText(unexpected);
        assertFalse(normalizedText.contains(normalizedUnexpected),
                "Не ожидалось вхождение: " + unexpected + ". Фрагмент заявления:\n" + preview(text));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String preview(String text) {
        int limit = Math.min(3000, Objects.requireNonNullElse(text, "").length());
        return Objects.requireNonNullElse(text, "").substring(0, limit);
    }
}
