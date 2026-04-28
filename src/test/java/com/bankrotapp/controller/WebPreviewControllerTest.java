package com.bankrotapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    void shouldRenderExpandedInputForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Данные должника")))
                .andExpect(content().string(containsString("Кредиторы")))
                .andExpect(content().string(containsString("Справки и документы")));
    }

    @Test
    void shouldRenderPreviewWithCalculatedTotals() throws Exception {
        mockMvc.perform(basePreviewRequest()
                        .param("creditorLines", "Банк А|Адрес А|loan|D1|01.01.2024|1000|док|1\nБанк А|Адрес А|loan|D2|02.01.2024|500|док|1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("общая сумма: 1\u00A0500,00 ₽")))
                .andExpect(content().string(containsString("Общая сумма долга:</strong> 1\u00A0500,00 ₽")));
    }

    @Test
    void shouldShowWarningsForSingleWithSpouseFields() throws Exception {
        mockMvc.perform(basePreviewRequest()
                        .param("maritalStatus", "SINGLE")
                        .param("spouseName", "Иванова А.А."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maritalStatus = SINGLE")))
                .andExpect(content().string(not(containsString("Состоит в зарегистрированном браке"))));
    }

    @Test
    void shouldRenderGenerateZipButtonOnPreviewPage() throws Exception {
        mockMvc.perform(basePreviewRequest())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/generate\"")))
                .andExpect(content().string(containsString("Сформировать ZIP с DOCX-документами")));
    }

    @Test
    void shouldRenderUnemployedStatusBlockWithoutEmployedPhrase() throws Exception {
        mockMvc.perform(basePreviewRequest().param("employmentStatus", "UNEMPLOYED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Официального места работы не имеет.")))
                .andExpect(content().string(not(containsString("Официально трудоустроен."))));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder basePreviewRequest() {
        return post("/preview")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("lastName", "Иванов")
                .param("firstName", "Иван")
                .param("middleName", "Иванович")
                .param("creditorLines", "Банк А|Адрес|loan|D1|01.01.2024|1000|док|1");
    }
}
