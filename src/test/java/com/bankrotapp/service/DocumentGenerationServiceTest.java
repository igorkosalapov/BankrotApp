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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentGenerationServiceTest {

    private final DocumentGenerationService service = new DocumentGenerationService(
            new DebtCalculationService(),
            new DocxTemplateRenderer()
    );

    @Test
    void testGeneratedStatementForIvanovDoesNotContainTemplateDebtorData() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertTrue(text.contains("Иванов Сергей Николаевич"));
        assertFalse(text.contains("Захаров"));
        assertFalse(text.contains("ВЭББАНКИР"));
        assertFalse(text.contains("ТУРБОЗАЙМ"));
        assertFalse(text.contains("МИГКРЕДИТ"));
        assertFalse(text.contains("MITSUBISHI RVR"));
    }

    @Test
    void testGeneratedStatementContainsCurrentCreditorsAndTotalDebt() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertTrue(text.contains("АО Альфа-Банк"));
        assertTrue(text.contains("ООО МКК Срочноденьги"));
        assertTrue(text.contains("ООО ПКО Право Онлайн"));
        assertTrue(text.contains("375 000,00") || text.contains("375\u00A0000,00"));
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
    void testNoUnresolvedPlaceholdersInGeneratedDocuments() throws Exception {
        byte[] zip = service.generateZip(ivanovClient());

        for (byte[] docx : readDocxEntries(zip)) {
            String xml = readWordXml(docx);
            assertFalse(xml.contains("{{"), "В DOCX остались неразрешённые placeholders {{...}}.");
            assertFalse(xml.contains("}}"), "В DOCX остались неразрешённые placeholders {{...}}.");
        }
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
                new FamilyInfo(false, "Иванова Мария Сергеевна", List.of()),
                new EmploymentInfo("UNEMPLOYED", "", "", BigDecimal.ZERO),
                new PropertyInfo(List.of(), List.of(), false)
        );
    }

    private String extract(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
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
}
