package com.bankrotapp.document;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxTemplateRendererTest {

    private final DocxTemplateRenderer renderer = new DocxTemplateRenderer();

    @Test
    void shouldReplaceSimplePlaceholders() throws Exception {
        byte[] template = buildTemplate();

        byte[] rendered = renderer.render(new ByteArrayInputStream(template), Map.of(
                "debtor.fullName", "Иванов Иван Иванович",
                "debtor.birthDate", "14.03.1989",
                "debtor.birthPlace", "г. Москва",
                "debtor.registrationAddress.fullAddress", "Россия, Москва, Тверская, д. 10, кв. 15",
                "debtor.inn", "770123456789",
                "debtor.snils", "112-233-445 95",
                "totalDebtFormatted", "175 000,00"
        ));

        String text = extract(rendered);

        assertTrue(text.contains("Иванов Иван Иванович"));
        assertTrue(text.contains("175 000,00"));
        assertFalse(text.contains("{{"));
    }

    private byte[] buildTemplate() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            document.createParagraph().createRun().setText("ФИО: {{debtor.fullName}}");
            document.createParagraph().createRun().setText("Дата рождения: {{debtor.birthDate}}");
            document.createParagraph().createRun().setText("Место рождения: {{debtor.birthPlace}}");
            document.createParagraph().createRun().setText("Адрес: {{debtor.registrationAddress.fullAddress}}");
            document.createParagraph().createRun().setText("ИНН: {{debtor.inn}}");
            document.createParagraph().createRun().setText("СНИЛС: {{debtor.snils}}");
            document.createParagraph().createRun().setText("Общий долг: {{totalDebtFormatted}}");

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String extract(byte[] documentBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
