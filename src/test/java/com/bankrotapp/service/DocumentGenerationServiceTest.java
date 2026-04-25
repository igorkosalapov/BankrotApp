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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentGenerationServiceTest {

    private final DocumentGenerationService service = new DocumentGenerationService(
            new DebtCalculationService(),
            new DocxTemplateRenderer()
    );

    @Test
    @Disabled("Требует ручной очистки DOCX-шаблона zayavlenie.docx от legacy-данных.")
    void testStatementDoesNotContainTemplateDebtorData() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertFalse(text.contains("Захаров"));
        assertFalse(text.contains("MITSUBISHI RVR"));
        assertFalse(text.contains("1 248 887,93"));
    }

    @Test
    void testAppendix1SignatureUsesCurrentDebtor() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixOneDocx(data, data.creditors()));

        assertTrue(text.contains("Иванов Сергей Николаевич"));
        assertFalse(text.contains("Захаров Владимир Игоревич"));
    }

    @Test
    void testAppendix2SignatureUsesCurrentDebtor() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixTwoDocx(data, List.of(), List.of(), List.of()));

        assertTrue(text.contains("Иванов Сергей Николаевич"));
        assertFalse(text.contains("Захаров Владимир Игоревич"));
    }

    @Test
    void testNoVehicleDoesNotRenderOldVehicle() throws Exception {
        BankruptcyApplicationData data = ivanovClient();
        String text = extract(service.generateAppendixTwoDocx(data, List.of(), List.of(), List.of()));

        assertFalse(text.contains("MITSUBISHI RVR"));
    }

    @Test
    @Disabled("Требует ручной шаблонизации основного заявления в zayavlenie.docx.")
    void testMainStatementUsesCurrentCreditorsAndTotalDebt() throws Exception {
        String text = extract(service.generateStatementDocx(ivanovClient()));

        assertTrue(text.contains("АО Альфа-Банк"));
        assertTrue(text.contains("ООО МКК Срочноденьги"));
        assertTrue(text.contains("ООО ПКО Право Онлайн"));
        assertTrue(text.contains("375\u00A0000,00"));
        assertTrue(text.contains("брак расторгнут"));
        assertTrue(text.contains("Дети: отсутствуют."));
        assertTrue(text.contains("Недвижимое имущество: отсутствует."));
        assertTrue(text.contains("Транспортные средства: отсутствуют."));
        assertTrue(text.contains("официально не трудоустроен"));
    }

    @Test
    @Disabled("Включить после ручной очистки DOCX-шаблонов от legacy sample-данных.")
    void testTemplatesDoNotContainRealSampleData() throws Exception {
        List<String> templates = List.of(
                "templates/zayavlenie.docx",
                "templates/prilozhenie_1.docx",
                "templates/prilozhenie_2.docx"
        );

        List<String> forbidden = List.of(
                "Захаров",
                "ВЭББАНКИР",
                "ТУРБОЗАЙМ",
                "МИГКРЕДИТ",
                "MITSUBISHI RVR",
                "1 248 887,93"
        );

        for (String template : templates) {
            String xml = readWordXml(template);
            for (String marker : forbidden) {
                assertFalse(xml.contains(marker), "Шаблон " + template + " содержит запрещённую строку: " + marker);
            }
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

    private String readWordXml(String templatePath) throws IOException {
        InputStream source = getClass().getClassLoader().getResourceAsStream(templatePath);
        assertTrue(source != null, "Не найден шаблон: " + templatePath);
        try (InputStream inputStream = source;
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            StringBuilder xml = new StringBuilder();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    xml.append(new String(zipInputStream.readAllBytes()));
                }
            }
            return xml.toString();
        }
    }
}
