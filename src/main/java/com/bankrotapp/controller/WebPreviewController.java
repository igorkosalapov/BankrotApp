package com.bankrotapp.controller;

import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.service.DebtCalculationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebPreviewController {

    private final DebtCalculationService debtCalculationService;

    public WebPreviewController(DebtCalculationService debtCalculationService) {
        this.debtCalculationService = debtCalculationService;
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

                        <label for="familyBlock">Семейный блок</label>
                        <textarea id="familyBlock" name="familyBlock" placeholder="В браке: да\nСупруг(а): Петрова А.А.\nДети: 2"></textarea>

                        <label for="propertyBlock">Блок имущества</label>
                        <textarea id="propertyBlock" name="propertyBlock" placeholder="Авто: Hyundai Solaris\nНедвижимость: квартира 45 м²\nИное ценное имущество: нет"></textarea>

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
                              @RequestParam(required = false, defaultValue = "") String familyBlock,
                              @RequestParam(required = false, defaultValue = "") String propertyBlock) {

        List<Creditor> creditors = parseCreditors(creditorLines);
        BigDecimal totalDebt = debtCalculationService.calculateTotalDebt(creditors);

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
}
