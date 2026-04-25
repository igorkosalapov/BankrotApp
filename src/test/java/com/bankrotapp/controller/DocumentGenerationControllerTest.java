package com.bankrotapp.controller;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
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
        MvcResult result = mockMvc.perform(post("/generate"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        String text;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            text = extractor.getText();
        }

        assertAll(
                () -> assertFalse(PLACEHOLDER_PATTERN.matcher(text).find(), "В сгенерированном документе остались placeholder-ы {{...}}."),
                () -> assertTrue(text.contains("Список кредиторов и должников гражданина"), "Документ должен быть на основе Приложения №1."),
                () -> assertTrue(text.contains("Иванов"), "Блок информации о гражданине должен быть заполнен."),
                () -> assertTrue(text.contains("Банк А"), "Таблица кредиторов должна содержать первого кредитора."),
                () -> assertTrue(text.contains("МФО Б"), "Таблица кредиторов должна содержать второго кредитора."),
                () -> assertFalse(text.contains("Захаров"), "Тестовые значения шаблона должны быть заменены в блоке гражданина.")
        );
    }
}
