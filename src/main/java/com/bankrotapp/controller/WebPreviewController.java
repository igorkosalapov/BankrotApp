package com.bankrotapp.controller;

import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.openai.OpenAiTextBlockService;
import com.bankrotapp.openai.OpenAiTextBlocks;
import com.bankrotapp.service.DebtCalculationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebPreviewController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DebtCalculationService debtCalculationService;
    private final OpenAiTextBlockService openAiTextBlockService;

    public WebPreviewController(DebtCalculationService debtCalculationService,
                                OpenAiTextBlockService openAiTextBlockService) {
        this.debtCalculationService = debtCalculationService;
        this.openAiTextBlockService = openAiTextBlockService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String formPage() {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8">
                    <title>BankrotApp — Ввод данных</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 2rem; max-width: 980px; }
                        label { display: block; margin-top: 1rem; font-weight: 600; }
                        input, textarea { width: 100%; margin-top: .4rem; padding: .6rem; box-sizing: border-box; }
                        textarea { min-height: 120px; }
                        button { margin-top: 1.2rem; padding: .7rem 1.1rem; cursor: pointer; }
                        .hint { color: #555; font-size: .9rem; margin-top: .2rem; }
                    </style>
                </head>
                <body>
                    <h1>Проверка ввода данных</h1>
                    <p>Этап предпросмотра: вводим данные и проверяем, как они отображаются.</p>

                    <form action="/preview" method="post">
                        <label for="fullName">ФИО должника</label>
                        <input id="fullName" name="fullName" placeholder="Иванов Иван Иванович" required>

                        <label for="creditorLines">Кредиторы / договоры / суммы</label>
                        <textarea id="creditorLines" name="creditorLines" placeholder="Банк А|Кредитный договор №123|150000\nМФО Б|Договор займа №77|25000" required></textarea>
                        <div class="hint">Формат: <code>Кредитор|Договор|Сумма</code>. Одна строка — один договор.</div>

                        <label for="maritalStatus">Семейное положение (SINGLE / MARRIED / DIVORCED / WIDOWED)</label>
                        <input id="maritalStatus" name="maritalStatus" placeholder="SINGLE">

                        <label for="spouseName">ФИО супруга(и)</label>
                        <input id="spouseName" name="spouseName" placeholder="Петрова Анна Александровна">

                        <label for="marriageDate">Дата брака (дд.мм.гггг)</label>
                        <input id="marriageDate" name="marriageDate" placeholder="01.06.2015">

                        <label for="marriageCertificate">Свидетельство о браке</label>
                        <input id="marriageCertificate" name="marriageCertificate" placeholder="IV-МЮ №123456">

                        <label for="divorceDate">Дата расторжения брака (дд.мм.гггг)</label>
                        <input id="divorceDate" name="divorceDate" placeholder="14.02.2020">

                        <label for="divorceCertificate">Свидетельство о расторжении брака</label>
                        <input id="divorceCertificate" name="divorceCertificate" placeholder="II-БР №654321">

                        <label for="spouseDeathDate">Дата смерти супруга(и) (дд.мм.гггг)</label>
                        <input id="spouseDeathDate" name="spouseDeathDate" placeholder="21.03.2021">

                        <label for="deathCertificate">Свидетельство о смерти</label>
                        <input id="deathCertificate" name="deathCertificate" placeholder="III-СМ №777888">

                        <label for="childrenLines">Дети (ФИО|дата рождения|свидетельство)</label>
                        <textarea id="childrenLines" name="childrenLines" placeholder="Иванов Петр Иванович|11.02.2014|I-АБ №123456"></textarea>

                        <label for="realEstateLines">Недвижимость (тип|описание)</label>
                        <textarea id="realEstateLines" name="realEstateLines" placeholder="Квартира|г. Москва, ул. Тверская, д. 10, кв. 15"></textarea>

                        <label for="vehicleLines">Транспорт (тип|марка|модель|госномер|год)</label>
                        <textarea id="vehicleLines" name="vehicleLines" placeholder="Легковой автомобиль|Hyundai|Solaris|А111АА77|2017"></textarea>

                        <label for="employmentStatus">Работа (EMPLOYED / UNEMPLOYED)</label>
                        <input id="employmentStatus" name="employmentStatus" placeholder="EMPLOYED">

                        <h2>Вспомогательные текстовые блоки (OpenAI только для этих полей)</h2>
                        <label for="hardshipReasonInput">Причина тяжелого финансового положения</label>
                        <textarea id="hardshipReasonInput" name="hardshipReasonInput" placeholder="Например: после сокращения дохода и роста обязательных платежей стало невозможно исполнять обязательства в полном объеме."></textarea>

                        <label for="employmentIncomeInput">Описание работы/доходов</label>
                        <textarea id="employmentIncomeInput" name="employmentIncomeInput" placeholder="Например: официально трудоустроен, получает ежемесячный доход, которого недостаточно для полного обслуживания долгов."></textarea>

                        <label for="loanFundsUsageInput">Описание использования заемных средств</label>
                        <textarea id="loanFundsUsageInput" name="loanFundsUsageInput" placeholder="Например: средства использованы на повседневные расходы семьи и исполнение первоочередных обязательств."></textarea>

                        <button type="submit">Показать предпросмотр</button>
                    </form>
                </body>
                </html>
                """;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String previewPage(@RequestParam String fullName,
                              @RequestParam String creditorLines,
                              @RequestParam(required = false, defaultValue = "SINGLE") String maritalStatus,
                              @RequestParam(required = false, defaultValue = "") String spouseName,
                              @RequestParam(required = false, defaultValue = "") String marriageDate,
                              @RequestParam(required = false, defaultValue = "") String marriageCertificate,
                              @RequestParam(required = false, defaultValue = "") String divorceDate,
                              @RequestParam(required = false, defaultValue = "") String divorceCertificate,
                              @RequestParam(required = false, defaultValue = "") String spouseDeathDate,
                              @RequestParam(required = false, defaultValue = "") String deathCertificate,
                              @RequestParam(required = false, defaultValue = "") String childrenLines,
                              @RequestParam(required = false, defaultValue = "") String realEstateLines,
                              @RequestParam(required = false, defaultValue = "") String vehicleLines,
                              @RequestParam(required = false, defaultValue = "EMPLOYED") String employmentStatus,
                              @RequestParam(required = false, defaultValue = "") String hardshipReasonInput,
                              @RequestParam(required = false, defaultValue = "") String employmentIncomeInput,
                              @RequestParam(required = false, defaultValue = "") String loanFundsUsageInput) {

        List<Creditor> creditors = parseCreditors(creditorLines);
        BigDecimal totalDebt = debtCalculationService.calculateTotalDebt(creditors);
        String familyBlock = buildFamilyBlock(maritalStatus, spouseName, marriageDate, marriageCertificate,
                divorceDate, divorceCertificate, spouseDeathDate, deathCertificate, childrenLines);
        String propertyBlock = buildPropertyBlock(realEstateLines, vehicleLines);
        String employmentBlock = buildEmploymentBlock(employmentStatus);
        OpenAiTextBlocks auxiliaryTextBlocks = openAiTextBlockService.generateAuxiliaryBlocks(
                hardshipReasonInput,
                employmentIncomeInput,
                loanFundsUsageInput
        );

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8">
                    <title>BankrotApp — Предпросмотр</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 2rem; max-width: 980px; }
                        section { border: 1px solid #ddd; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; }
                        h2 { margin-top: 0; }
                        ul { margin: .4rem 0 .8rem 1.2rem; }
                    </style>
                </head>
                <body>
                    <h1>Предпросмотр данных заявления</h1>
                """);

        html.append("<section><h2>ФИО должника</h2><p>")
                .append(escape(fullName))
                .append("</p></section>");

        html.append("<section><h2>Шапка заявления</h2>");
        if (creditors.isEmpty()) {
            html.append("<p>Кредиторы не указаны.</p>");
        } else {
            for (int i = 0; i < creditors.size(); i++) {
                Creditor creditor = creditors.get(i);
                html.append("<p><strong>Кредитор ")
                        .append(i + 1)
                        .append(":</strong> ")
                        .append(escape(creditor.name()))
                        .append("</p>");
            }
        }
        html.append("</section>");

        html.append("<section><h2>Текст заявления: задолженность по кредиторам</h2>");
        if (creditors.isEmpty()) {
            html.append("<p>Кредиторы не указаны.</p>");
        } else {
            for (Creditor creditor : creditors) {
                appendCreditorDebtBlock(html, creditor);
            }
        }
        html.append("<p><strong>Общая сумма долга:</strong> ")
                .append(debtCalculationService.formatAmountRu(totalDebt))
                .append(" ₽</p>")
                .append("</section>");

        html.append("<section><h2>Семейный блок</h2><pre>")
                .append(escape(familyBlock.isBlank() ? "Нет данных" : familyBlock))
                .append("</pre></section>");

        html.append("<section><h2>Блок имущества</h2><pre>")
                .append(escape(propertyBlock.isBlank() ? "Нет данных" : propertyBlock))
                .append("</pre></section>");

        html.append("<section><h2>Блок занятости</h2><pre>")
                .append(escape(employmentBlock))
                .append("</pre></section>");

        html.append("<section><h2>Вспомогательные блоки перед генерацией DOCX</h2>")
                .append("<p><strong>Источник:</strong> ")
                .append(auxiliaryTextBlocks.generatedByOpenAi() ? "OpenAI API" : "Пользовательский/стандартный fallback")
                .append("</p>")
                .append("<p><strong>Причина тяжелого финансового положения:</strong> ")
                .append(escape(auxiliaryTextBlocks.hardshipReason()))
                .append("</p>")
                .append("<p><strong>Описание работы/доходов:</strong> ")
                .append(escape(auxiliaryTextBlocks.employmentIncomeDescription()))
                .append("</p>")
                .append("<p><strong>На что были потрачены заемные средства:</strong> ")
                .append(escape(auxiliaryTextBlocks.loanFundsUsageDescription()))
                .append("</p>")
                .append("</section>");

        html.append("<p><a href='/'>← Назад к форме</a></p>")
                .append("</body></html>");

        return html.toString();
    }

    private List<Creditor> parseCreditors(String creditorLines) {
        if (creditorLines == null || creditorLines.isBlank()) {
            return List.of();
        }

        Map<String, List<Contract>> grouped = new LinkedHashMap<>();

        String[] lines = creditorLines.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\|", 3);
            if (parts.length < 3) {
                continue;
            }

            String creditorName = parts[0].trim();
            String contractName = parts[1].trim();
            BigDecimal amount = parseAmount(parts[2].trim());

            Contract contract = new Contract(contractName, "manual", amount, BigDecimal.ZERO, BigDecimal.ZERO);
            grouped.computeIfAbsent(creditorName, key -> new ArrayList<>()).add(contract);
        }

        List<Creditor> result = new ArrayList<>();
        for (Map.Entry<String, List<Contract>> entry : grouped.entrySet()) {
            result.add(new Creditor(entry.getKey(), "", entry.getValue()));
        }

        return result;
    }

    private BigDecimal parseAmount(String rawAmount) {
        String normalized = rawAmount.replace(" ", "").replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void appendCreditorDebtBlock(StringBuilder html, Creditor creditor) {
        List<Contract> contracts = creditor.contracts();
        BigDecimal creditorTotal = debtCalculationService.calculateCreditorTotal(creditor);

        html.append("<article>");
        html.append("<h3>Задолженность перед ")
                .append(escape(creditor.name()));

        if (contracts.size() > 1) {
            html.append(" (общая сумма: ")
                    .append(debtCalculationService.formatAmountRu(creditorTotal))
                    .append(" ₽)");
        }
        html.append("</h3>");

        if (contracts.isEmpty()) {
            html.append("<p>Договоры не указаны.</p>");
        } else if (contracts.size() == 1) {
            Contract contract = contracts.get(0);
            html.append("<p><strong>Основание возникновения задолженности:</strong> ")
                    .append(escape(contract.contractNumber()))
                    .append("</p>");
            html.append("<p><strong>Подтверждающий документ:</strong> ")
                    .append(escape(contract.contractNumber()))
                    .append("</p>");
            html.append("<p><strong>Сумма долга:</strong> ")
                    .append(debtCalculationService.formatAmountRu(contract.principalDebt()))
                    .append(" ₽</p>");
        } else {
            html.append("<ul>");
            for (Contract contract : contracts) {
                html.append("<li>")
                        .append(escape(contract.contractNumber()))
                        .append(" — ")
                        .append(debtCalculationService.formatAmountRu(contract.principalDebt()))
                        .append(" ₽</li>");
            }
            html.append("</ul>");
        }

        html.append("</article>");
    }

    private String buildFamilyBlock(String maritalStatus,
                                    String spouseName,
                                    String marriageDate,
                                    String marriageCertificate,
                                    String divorceDate,
                                    String divorceCertificate,
                                    String spouseDeathDate,
                                    String deathCertificate,
                                    String childrenLines) {
        StringBuilder block = new StringBuilder();
        String normalizedStatus = maritalStatus == null ? "" : maritalStatus.trim().toUpperCase();

        switch (normalizedStatus) {
            case "MARRIED" -> block.append("Состоит в зарегистрированном браке");
            case "DIVORCED" -> block.append("Брак расторгнут");
            case "WIDOWED" -> block.append("Супруг(а) умер(ла)");
            case "SINGLE" -> block.append("В браке не состоит.");
            default -> block.append("В браке не состоит.");
        }

        if ("MARRIED".equals(normalizedStatus)) {
            if (!spouseName.isBlank()) {
                block.append(" с ").append(spouseName.trim());
            }
            if (!marriageDate.isBlank()) {
                block.append(", дата регистрации брака: ").append(formatDate(marriageDate));
            }
            if (!marriageCertificate.isBlank()) {
                block.append(", свидетельство о заключении брака: ").append(marriageCertificate.trim());
            }
            block.append(".");
        }

        if ("DIVORCED".equals(normalizedStatus)) {
            if (!divorceDate.isBlank()) {
                block.append(", дата расторжения брака: ").append(formatDate(divorceDate));
            }
            if (!divorceCertificate.isBlank()) {
                block.append(", свидетельство о расторжении брака: ").append(divorceCertificate.trim());
            }
            block.append(".");
        }

        if ("WIDOWED".equals(normalizedStatus)) {
            if (!spouseName.isBlank()) {
                block.append(" ").append(spouseName.trim());
            }
            if (!spouseDeathDate.isBlank()) {
                block.append(", дата смерти: ").append(formatDate(spouseDeathDate));
            }
            if (!deathCertificate.isBlank()) {
                block.append(", свидетельство о смерти: ").append(deathCertificate.trim());
            }
            block.append(".");
        }

        List<String[]> children = parseStructuredLines(childrenLines, 3);
        block.append(System.lineSeparator());
        if (children.isEmpty()) {
            block.append("Несовершеннолетних детей на иждивении не имеет.");
        } else {
            block.append("Несовершеннолетние дети:");
            for (String[] child : children) {
                block.append(System.lineSeparator())
                        .append("- ")
                        .append(child[0])
                        .append(", дата рождения: ")
                        .append(formatDate(child[1]))
                        .append(", свидетельство о рождении: ")
                        .append(child[2]);
            }
        }

        return block.toString();
    }

    private String buildPropertyBlock(String realEstateLines, String vehicleLines) {
        StringBuilder block = new StringBuilder();
        List<String[]> realEstateItems = parseStructuredLines(realEstateLines, 2);
        List<String[]> vehicles = parseStructuredLines(vehicleLines, 5);

        if (realEstateItems.isEmpty()) {
            block.append("Объекты недвижимости в собственности отсутствуют.");
        } else {
            block.append("В собственности имеется недвижимое имущество:");
            for (String[] item : realEstateItems) {
                block.append(System.lineSeparator())
                        .append("- ")
                        .append(item[0])
                        .append(": ")
                        .append(item[1]);
            }
        }

        block.append(System.lineSeparator());
        if (vehicles.isEmpty()) {
            block.append("Транспортные средства отсутствуют.");
        } else {
            block.append("Транспортные средства:");
            for (String[] vehicle : vehicles) {
                block.append(System.lineSeparator())
                        .append("- ")
                        .append(vehicle[0])
                        .append(" ")
                        .append(vehicle[1])
                        .append(" ")
                        .append(vehicle[2])
                        .append(", гос. номер: ")
                        .append(vehicle[3])
                        .append(", год выпуска: ")
                        .append(vehicle[4]);
            }
        }

        return block.toString();
    }

    private String buildEmploymentBlock(String employmentStatus) {
        String normalizedStatus = employmentStatus == null ? "" : employmentStatus.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "UNEMPLOYED" -> "Официального места работы не имеет.";
            case "EMPLOYED" -> "Официально трудоустроен.";
            default -> "Сведения о занятости не предоставлены.";
        };
    }

    private List<String[]> parseStructuredLines(String lines, int columns) {
        if (lines == null || lines.isBlank()) {
            return List.of();
        }

        List<String[]> parsed = new ArrayList<>();
        for (String line : lines.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("\\|", -1);
            if (parts.length < columns) {
                continue;
            }

            String[] normalized = new String[columns];
            for (int i = 0; i < columns; i++) {
                normalized[i] = parts[i].trim();
            }
            parsed.add(normalized);
        }

        return parsed;
    }

    private String formatDate(String dateValue) {
        if (dateValue == null || dateValue.isBlank()) {
            return "";
        }

        try {
            return LocalDate.parse(dateValue.trim(), DATE_FORMATTER).format(DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return dateValue.trim();
        }
    }
}
