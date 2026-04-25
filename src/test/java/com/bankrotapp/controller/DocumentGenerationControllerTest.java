package com.bankrotapp.controller;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentGenerationControllerTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateAppendixOneDocxWithoutUnresolvedPlaceholders() throws Exception {
        MvcResult result = mockMvc.perform(post("/generate/appendix-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        String text;
        String surnameInCitizenBlock;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();

            XWPFTable citizenTable = document.getTables().get(0);
            surnameInCitizenBlock = citizenTable.getRow(1).getCell(2).getText().trim();
        }

        assertAll(
                () -> assertFalse(PLACEHOLDER_PATTERN.matcher(text).find(), "В сгенерированном документе остались placeholder-ы {{...}}."),
                () -> assertTrue(text.contains("Список кредиторов и должников гражданина"), "Документ должен быть на основе Приложения №1."),
                () -> assertEquals("Иванов", surnameInCitizenBlock, "Фамилия в блоке гражданина должна быть заменена данными должника."),
                () -> assertTrue(text.contains("Банк А"), "Таблица кредиторов должна содержать первого кредитора."),
                () -> assertTrue(text.contains("МФО Б"), "Таблица кредиторов должна содержать второго кредитора.")
        );
    }

    @Test
    void shouldUseRequestBodyDataForAppendixOneGeneration() throws Exception {
        String payload = """
                {
                  "debtor": {
                    "fullName": "Петров Петр Петрович",
                    "birthDate": "1990-01-01",
                    "snils": "111-111-111 11",
                    "inn": "123456789012",
                    "passportNumber": "4500 000000",
                    "registrationAddress": {
                      "country": "Россия",
                      "region": "г. Москва",
                      "city": "Москва",
                      "street": "Арбат",
                      "house": "1",
                      "apartment": "1",
                      "postalCode": "119019"
                    },
                    "actualAddress": {
                      "country": "Россия",
                      "region": "г. Москва",
                      "city": "Москва",
                      "street": "Арбат",
                      "house": "1",
                      "apartment": "1",
                      "postalCode": "119019"
                    },
                    "phone": "79000000000",
                    "email": "petrov@example.com",
                    "birthPlace": "г. Москва"
                  },
                  "creditors": [
                    {
                      "name": "Кредитор Тест",
                      "inn": "0000000000",
                      "contracts": [
                        {
                          "contractNumber": "Договор №1",
                          "contractType": "loan",
                          "principalDebt": 1000.00,
                          "interestDebt": 100.00,
                          "penalties": 10.00
                        }
                      ]
                    }
                  ]
                }
                """;

        MvcResult result = mockMvc.perform(post("/generate/appendix-1")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        String text;
        String surnameInCitizenBlock;
        String totalAmountCell;
        String debtAmountCell;
        String penaltiesCell;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();

            XWPFTable citizenTable = document.getTables().get(0);
            surnameInCitizenBlock = citizenTable.getRow(1).getCell(2).getText().trim();

            XWPFTable creditorsTable = document.getTables().get(1);
            totalAmountCell = creditorsTable.getRow(4).getCell(5).getText().trim();
            debtAmountCell = creditorsTable.getRow(4).getCell(6).getText().trim();
            penaltiesCell = creditorsTable.getRow(4).getCell(7).getText().trim();
        }

        assertAll(
                () -> assertEquals("Петров", surnameInCitizenBlock, "Должно использоваться ФИО из request body."),
                () -> assertTrue(text.contains("Кредитор Тест"), "Должен использоваться кредитор из request body."),
                () -> assertEquals("1\u00A0110,00", totalAmountCell, "Колонка общей суммы должна учитывать principal + interest + penalties."),
                () -> assertEquals("1\u00A0100,00", debtAmountCell, "Колонка суммы задолженности должна учитывать principal + interest."),
                () -> assertEquals("10,00", penaltiesCell, "Колонка неустоек должна содержать penalties.")
        );
    }

    @Test
    void shouldGenerateZipWithThreeDocxFilesAndDebtorFioInFileNames() throws Exception {
        MvcResult result = mockMvc.perform(post("/generate"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/zip")))
                .andReturn();

        byte[] zipBytes = result.getResponse().getContentAsByteArray();
        List<String> fileNames = new ArrayList<>();
        String[] appendixOneText = {""};

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                fileNames.add(entry.getName());

                if (entry.getName().startsWith("Prilozhenie_1_")) {
                    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(readEntryBytes(zipInputStream)));
                         XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                        appendixOneText[0] = extractor.getText();
                    }
                }
            }
        }

        assertAll(
                () -> assertEquals(3, fileNames.size(), "ZIP должен содержать три DOCX-файла."),
                () -> assertTrue(fileNames.stream().anyMatch(name -> name.equals("Zayavlenie_Иванов_Иван_Иванович.docx")), "ZIP должен содержать заявление с ФИО в имени файла."),
                () -> assertTrue(fileNames.stream().anyMatch(name -> name.equals("Prilozhenie_1_Иванов_Иван_Иванович.docx")), "ZIP должен содержать приложение №1 с ФИО в имени файла."),
                () -> assertTrue(fileNames.stream().anyMatch(name -> name.equals("Prilozhenie_2_Иванов_Иван_Иванович.docx")), "ZIP должен содержать приложение №2 с ФИО в имени файла."),
                () -> assertTrue(appendixOneText[0].contains("Список кредиторов и должников гражданина"), "Приложение №1 в ZIP должно корректно генерироваться.")
        );
    }

    @Test
    void shouldGenerateAppendixTwoDocxWithoutUnresolvedPlaceholders() throws Exception {
        MvcResult result = mockMvc.perform(post("/generate/appendix-2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        String text;
        String surnameInCitizenBlock;
        String apartmentAddress;
        String passengerCarBlock;
        String bankCellValue;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();

            XWPFTable citizenTable = document.getTables().get(0);
            surnameInCitizenBlock = citizenTable.getRow(1).getCell(2).getText().trim();

            XWPFTable realEstateTable = document.getTables().get(1);
            apartmentAddress = realEstateTable.getRow(6).getCell(3).getText().trim();

            XWPFTable vehicleTable = document.getTables().get(2);
            passengerCarBlock = vehicleTable.getRow(2).getCell(1).getText().trim();

            XWPFTable bankAccountsTable = document.getTables().get(3);
            bankCellValue = bankAccountsTable.getRow(2).getCell(1).getText().trim();
        }

        assertAll(
                () -> assertFalse(PLACEHOLDER_PATTERN.matcher(text).find(), "В сгенерированном документе остались placeholder-ы {{...}}."),
                () -> assertTrue(text.contains("Опись имущества гражданина"), "Документ должен быть на основе Приложения №2."),
                () -> assertEquals("Иванов", surnameInCitizenBlock, "Фамилия в блоке гражданина должна быть заменена данными должника."),
                () -> assertTrue(apartmentAddress.contains("г. Москва"), "Категория квартир должна быть заполнена адресом."),
                () -> assertTrue(passengerCarBlock.contains("Hyundai Solaris 2017"), "Категория легковых автомобилей должна быть заполнена транспортом."),
                () -> assertEquals("-", bankCellValue, "При отсутствии банковских счетов должны проставляться прочерки.")
        );
    }

    private byte[] readEntryBytes(ZipInputStream zipInputStream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zipInputStream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
