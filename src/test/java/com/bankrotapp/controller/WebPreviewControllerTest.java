package com.bankrotapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    void shouldRenderPreviewWithCalculatedTotals() throws Exception {
        mockMvc.perform(post("/preview")
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines",
                                "Банк А|Договор 1|1000\nБанк А|Договор 2|250,50\nМФО Б|Договор 3|149.50")
                        .param("familyBlock", "В браке: нет")
                        .param("propertyBlock", "Авто: нет"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Иванов Иван Иванович")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сумма по кредитору:</strong> 1\u00A0250,50 ₽")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Общая сумма долга:</strong> 1\u00A0400,00 ₽")));
    }
}
