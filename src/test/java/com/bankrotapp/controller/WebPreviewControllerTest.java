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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<form action=\"/preview\" method=\"post\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Вспомогательные текстовые блоки (OpenAI только для этих полей)")));
    }

    @Test
    void shouldRenderSingleCreditorSingleContractBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Кредитный договор №1|1000"))
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

    @Test
    void shouldRenderSingleMaritalStatusBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("maritalStatus", "SINGLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("В браке не состоит.")));
    }

    @Test
    void shouldRenderMarriedMaritalStatusBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("maritalStatus", "MARRIED")
                        .param("spouseName", "Иванова Анна Петровна")
                        .param("marriageDate", "01.06.2015")
                        .param("marriageCertificate", "IV-МЮ №123456"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Состоит в зарегистрированном браке с Иванова Анна Петровна, дата регистрации брака: 01.06.2015, свидетельство о заключении брака: IV-МЮ №123456.")));
    }

    @Test
    void shouldRenderDivorcedMaritalStatusBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("maritalStatus", "DIVORCED")
                        .param("divorceDate", "14.02.2020")
                        .param("divorceCertificate", "II-БР №654321"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Брак расторгнут, дата расторжения брака: 14.02.2020, свидетельство о расторжении брака: II-БР №654321.")));
    }

    @Test
    void shouldRenderWidowedMaritalStatusBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("maritalStatus", "WIDOWED")
                        .param("spouseName", "Иванова Анна Петровна")
                        .param("spouseDeathDate", "21.03.2021")
                        .param("deathCertificate", "III-СМ №777888"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Супруг(а) умер(ла) Иванова Анна Петровна, дата смерти: 21.03.2021, свидетельство о смерти: III-СМ №777888.")));
    }

    @Test
    void shouldRenderNoChildrenBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("childrenLines", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Несовершеннолетних детей на иждивении не имеет.")));
    }

    @Test
    void shouldRenderChildrenListBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("childrenLines", "Иванов Петр Иванович|11.02.2014|I-АБ №123456"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Несовершеннолетние дети:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Иванов Петр Иванович, дата рождения: 11.02.2014, свидетельство о рождении: I-АБ №123456")));
    }

    @Test
    void shouldRenderNoRealEstateBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("realEstateLines", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Объекты недвижимости в собственности отсутствуют.")));
    }

    @Test
    void shouldRenderRealEstatePresenceBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("realEstateLines", "Квартира|г. Москва, ул. Тверская, д. 10, кв. 15"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("В собственности имеется недвижимое имущество:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Квартира: г. Москва, ул. Тверская, д. 10, кв. 15")));
    }

    @Test
    void shouldRenderNoVehiclesBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("vehicleLines", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Транспортные средства отсутствуют.")));
    }

    @Test
    void shouldRenderVehiclesListBlock() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("vehicleLines", "Легковой автомобиль|Hyundai|Solaris|А111АА77|2017"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Транспортные средства:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Легковой автомобиль Hyundai Solaris, гос. номер: А111АА77, год выпуска: 2017")));
    }

    @Test
    void shouldRenderAuxiliaryBlocksWithDefaultsWhenOpenAiUnavailable() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Вспомогательные блоки перед генерацией DOCX")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Пользовательский/стандартный fallback")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Причина тяжелого финансового положения")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Описание работы/доходов")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("На что были потрачены заемные средства")));
    }

    @Test
    void shouldUseUserTextAsFallbackWhenProvided() throws Exception {
        mockMvc.perform(post("/preview")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Иванов Иван Иванович")
                        .param("creditorLines", "Банк А|Договор|1000")
                        .param("hardshipReasonInput", "Потеря части дохода после сокращения премий.")
                        .param("employmentIncomeInput", "Работаю по трудовому договору, доход нестабилен.")
                        .param("loanFundsUsageInput", "Заемные средства использованы на базовые бытовые расходы."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Потеря части дохода после сокращения премий.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Работаю по трудовому договору, доход нестабилен.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Заемные средства использованы на базовые бытовые расходы.")));
    }
}
