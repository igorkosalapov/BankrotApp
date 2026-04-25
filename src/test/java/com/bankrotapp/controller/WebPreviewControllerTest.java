package com.bankrotapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderInputForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<form action=\"/preview\" method=\"post\">")));
    }

    @Test
    void shouldRenderSingleCreditorSingleContractBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Кредитный договор №1|1000")
                        .param("familyBlock", "В браке: нет")
                        .param("propertyBlock", "Авто: нет"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Иванов Иван Иванович")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Кредитор 1:</strong> Банк А")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Основание возникновения задолженности:</strong> Кредитный договор №1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Подтверждающий документ:</strong> Кредитный договор №1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сумма долга:</strong> 1\u00A0000,00 ₽")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Общая сумма долга:</strong> 1\u00A0000,00 ₽")));
    }

    @Test
    void shouldRenderSingleCreditorMultipleContractsBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор 1|1000\nБанк А|Договор 2|250,50"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Задолженность перед Банк А (общая сумма: 1\u00A0250,50 ₽)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Договор 1 — 1\u00A0000,00 ₽")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Договор 2 — 250,50 ₽")));
    }

    @Test
    void shouldRenderMultipleCreditorsInHeader() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор 1|1000\nМФО Б|Договор 2|500"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Кредитор 1:</strong> Банк А")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Кредитор 2:</strong> МФО Б")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Задолженность перед Банк А")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Задолженность перед МФО Б")));
    }

    @Test
    void shouldCalculateTotalDebtAutomatically() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор 1|1000\nБанк А|Договор 2|250,50\nМФО Б|Договор 3|149.50"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Общая сумма долга:</strong> 1\u00A0400,00 ₽")));
    }
}
