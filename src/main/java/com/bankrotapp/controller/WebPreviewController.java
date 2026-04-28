package com.bankrotapp.controller;

import com.bankrotapp.model.Address;
import com.bankrotapp.model.BankruptcyApplicationData;
import com.bankrotapp.model.Child;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.model.EmploymentInfo;
import com.bankrotapp.model.FamilyInfo;
import com.bankrotapp.model.PreviewFormData;
import com.bankrotapp.model.PropertyInfo;
import com.bankrotapp.model.RealEstateItem;
import com.bankrotapp.model.Vehicle;
import com.bankrotapp.openai.OpenAiTextBlockService;
import com.bankrotapp.openai.OpenAiTextBlocks;
import com.bankrotapp.service.DebtCalculationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class WebPreviewController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DebtCalculationService debtCalculationService;
    private final OpenAiTextBlockService openAiTextBlockService;
    private final PreviewInputParser previewInputParser;

    public WebPreviewController(DebtCalculationService debtCalculationService,
                                OpenAiTextBlockService openAiTextBlockService) {
        this.debtCalculationService = debtCalculationService;
        this.openAiTextBlockService = openAiTextBlockService;
        this.previewInputParser = new PreviewInputParser();
    }

    @GetMapping(value = "/", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String formPage() {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8">
                    <title>BankrotApp — Ввод данных</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 2rem; max-width: 1080px; }
                        label { display: block; margin-top: .7rem; font-weight: 600; }
                        input, textarea { width: 100%; margin-top: .2rem; padding: .6rem; box-sizing: border-box; }
                        textarea { min-height: 90px; }
                        button { margin-top: 1.2rem; padding: .7rem 1.1rem; cursor: pointer; }
                        .hint { color: #555; font-size: .9rem; margin-top: .2rem; }
                        fieldset { margin-top: 1rem; border: 1px solid #ddd; border-radius: 8px; }
                        legend { font-weight: 700; }
                    </style>
                </head>
                <body>
                    <h1>Проверка ввода данных</h1>
                    <form action="/preview" method="post">
                        <fieldset><legend>1. Данные должника</legend>
                            <label>Фамилия<input name="lastName" required></label>
                            <label>Имя<input name="firstName" required></label>
                            <label>Отчество<input name="middleName"></label>
                            <label>Дата рождения (дд.мм.гггг)<input name="birthDate"></label>
                            <label>Место рождения<input name="birthPlace"></label>
                            <label>СНИЛС<input name="snils"></label>
                            <label>ИНН<input name="inn"></label>
                            <label>Паспорт: серия<input name="passportSeries"></label>
                            <label>Паспорт: номер<input name="passportNumber"></label>
                            <label>Кем выдан<input name="passportIssuedBy"></label>
                            <label>Дата выдачи<input name="passportIssueDate"></label>
                            <label>Код подразделения<input name="passportDivisionCode"></label>
                            <label>Индекс<input name="registrationPostalCode"></label>
                            <label>Регион<input name="registrationRegion"></label>
                            <label>Район<input name="registrationDistrict"></label>
                            <label>Город<input name="registrationCity"></label>
                            <label>Населенный пункт<input name="registrationSettlement"></label>
                            <label>Улица<input name="registrationStreet"></label>
                            <label>Дом<input name="registrationHouse"></label>
                            <label>Корпус<input name="registrationBuilding"></label>
                            <label>Квартира<input name="registrationApartment"></label>
                            <label>Полный адрес (опционально)<input name="registrationFullAddress"></label>
                        </fieldset>

                        <fieldset><legend>2. Кредиторы</legend>
                            <textarea name="creditorLines" required placeholder="Кредитор|Адрес кредитора|Тип договора|Номер договора|Дата договора|Сумма|Подтверждающий документ|Листов"></textarea>
                            <div class="hint">Поддерживается и старый формат: Кредитор|Договор|Сумма.</div>
                        </fieldset>

                        <fieldset><legend>3–4. Семья и дети</legend>
                            <label>Семейное положение<input name="maritalStatus" placeholder="SINGLE / MARRIED / DIVORCED / WIDOWED"></label>
                            <label>ФИО супруга/бывшего супруга<input name="spouseName"></label>
                            <label>Дата брака<input name="marriageDate"></label>
                            <label>Свидетельство о браке<input name="marriageCertificate"></label>
                            <label>Дата расторжения<input name="divorceDate"></label>
                            <label>Свидетельство о расторжении<input name="divorceCertificate"></label>
                            <label>Дата смерти супруга<input name="spouseDeathDate"></label>
                            <label>Свидетельство о смерти<input name="deathCertificate"></label>
                            <label>Дети (ФИО|дата рождения|свидетельство о рождении)
                                <textarea name="childrenLines"></textarea>
                            </label>
                        </fieldset>

                        <fieldset><legend>5. Недвижимость</legend>
                            <textarea name="realEstateLines" placeholder="Тип|адрес|вид собственности|площадь|основание приобретения|стоимость|залог"></textarea>
                        </fieldset>

                        <fieldset><legend>6. Транспорт</legend>
                            <textarea name="vehicleLines" placeholder="Тип|марка|модель|госномер|год|VIN/кузов|номер двигателя|вид собственности|место хранения|стоимость|залог"></textarea>
                        </fieldset>

                        <fieldset><legend>7. Работа и доходы</legend>
                            <label>employmentStatus<input name="employmentStatus" placeholder="EMPLOYED / UNEMPLOYED / INFORMAL_WORK / PENSIONER / OTHER"></label>
                            <label>Работодатель<input name="employerName"></label>
                            <label>Должность<input name="position"></label>
                            <label>Ежемесячный доход<input name="monthlyIncome"></label>
                            <label>Доход за 2022<input name="income2022"></label>
                            <label>Доход за 2023<input name="income2023"></label>
                            <label>Доход за 2024<input name="income2024"></label>
                            <label>Доход за 2025<input name="income2025"></label>
                            <label>Описание предыдущей работы/подработок<textarea name="previousWorkDescription"></textarea></label>
                        </fieldset>

                        <fieldset><legend>8. Справки и документы</legend>
                            <label>Номер и дата справки ЕГРИП<input name="egripCertificate"></label>
                            <label>Номер и дата ответа ГИБДД<input name="gibddResponse"></label>
                            <label>Номер и дата выписки/уведомления ЕГРН<input name="egrnExtract"></label>
                            <label>Листов паспорта<input name="passportPages"></label>
                            <label>Листов ИНН<input name="innPages"></label>
                            <label>Листов СНИЛС<input name="snilsPages"></label>
                            <label>Листов СЗИ-ИЛС<input name="sziIlsPages"></label>
                            <label>Листов сведений о счетах<input name="bankAccountsPages"></label>
                            <label>Листов справки налоговой<input name="taxCertificatePages"></label>
                            <label>Листов справки о судимости<input name="criminalRecordPages"></label>
                            <label>Листов доказательств отправки кредиторам<input name="creditorPostingProofPages"></label>
                        </fieldset>

                        <fieldset><legend>9. Вспомогательные текстовые блоки</legend>
                            <label>Причина тяжелого финансового положения<textarea name="hardshipReasonInput"></textarea></label>
                            <label>Описание работы/доходов<textarea name="employmentIncomeInput"></textarea></label>
                            <label>Описание использования заемных средств<textarea name="loanFundsUsageInput"></textarea></label>
                        </fieldset>

                        <button type="submit">Показать предпросмотр</button>
                    </form>
                </body>
                </html>
                """;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String previewPage(@ModelAttribute PreviewFormData form, HttpSession session) {
        List<Creditor> creditors = previewInputParser.parseCreditors(form.getCreditorLines());
        List<RealEstateItem> realEstateItems = previewInputParser.parseRealEstateItems(form.getRealEstateLines());
        List<Vehicle> vehicles = previewInputParser.parseVehicles(form.getVehicleLines());
        List<Child> children = previewInputParser.parseChildren(form.getChildrenLines());

        BankruptcyApplicationData dataForDocx = buildApplicationData(form, creditors, realEstateItems, vehicles, children);
        session.setAttribute(DocumentGenerationController.SESSION_PREVIEW_DATA, dataForDocx);

        BigDecimal totalDebt = debtCalculationService.calculateTotalDebt(creditors);
        String familyBlock = buildFamilyBlock(form, children);
        String propertyBlock = buildPropertyBlock(form.getRealEstateLines(), form.getVehicleLines());
        String employmentBlock = buildEmploymentBlock(form.getEmploymentStatus());
        OpenAiTextBlocks auxiliaryTextBlocks = openAiTextBlockService.generateAuxiliaryBlocks(
                form.getHardshipReasonInput(),
                form.getEmploymentIncomeInput(),
                form.getLoanFundsUsageInput()
        );

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="ru"><head><meta charset="UTF-8"><title>BankrotApp — Предпросмотр</title>
                <style>body{font-family:Arial,sans-serif;margin:2rem;max-width:1080px;}section{border:1px solid #ddd;border-radius:8px;padding:1rem;margin-bottom:1rem;} .warn{color:#b00020;font-weight:700;}</style>
                </head><body><h1>Предпросмотр данных заявления</h1>
                """);

        html.append("<section><h2>Данные должника</h2><ul>")
                .append(li("ФИО", form.getFullName()))
                .append(li("Дата рождения", form.getBirthDate()))
                .append(li("Место рождения", form.getBirthPlace()))
                .append(li("СНИЛС", form.getSnils()))
                .append(li("ИНН", form.getInn()))
                .append(li("Паспорт", form.getPassportSeries() + " " + form.getPassportNumber()))
                .append(li("Кем выдан", form.getPassportIssuedBy()))
                .append(li("Дата выдачи", form.getPassportIssueDate()))
                .append(li("Код подразделения", form.getPassportDivisionCode()))
                .append(li("Адрес", effectiveAddress(form)))
                .append("</ul></section>");

        html.append("<section><h2>Кредиторы и суммы</h2>");
        for (Creditor creditor : creditors) {
            appendCreditorDebtBlock(html, creditor);
        }
        html.append("<p><strong>Общая сумма долга:</strong> ")
                .append(debtCalculationService.formatAmountRu(totalDebt))
                .append(" ₽</p></section>");

        html.append("<section><h2>Сформированные блоки</h2><ul>")
                .append(li("Семейный блок", familyBlock))
                .append(li("Дети", children.isEmpty() ? "детей нет" : "дети указаны: " + children.size()))
                .append(li("Недвижимость", realEstateItems.isEmpty() ? "нет" : "объектов: " + realEstateItems.size()))
                .append(li("Транспорт", vehicles.isEmpty() ? "нет" : "единиц: " + vehicles.size()))
                .append(li("Занятость", employmentBlock))
                .append(li("Приложения", "данные по справкам/листам заполнены в соответствующем блоке"))
                .append("</ul></section>");

        html.append("<section><h2>Предупреждения</h2>");
        List<String> warnings = buildWarnings(form);
        if (warnings.isEmpty()) {
            html.append("<p>Нет.</p>");
        } else {
            for (String warning : warnings) {
                html.append("<p class='warn'>").append(escape(warning)).append("</p>");
            }
        }
        html.append("</section>");

        html.append("<section><h2>Справки и документы</h2><ul>")
                .append(li("ЕГРИП", form.getEgripCertificate()))
                .append(li("ГИБДД", form.getGibddResponse()))
                .append(li("ЕГРН", form.getEgrnExtract()))
                .append(li("Листы паспорта", form.getPassportPages()))
                .append(li("Листы ИНН", form.getInnPages()))
                .append(li("Листы СНИЛС", form.getSnilsPages()))
                .append(li("Листы СЗИ-ИЛС", form.getSziIlsPages()))
                .append(li("Листы сведений о счетах", form.getBankAccountsPages()))
                .append(li("Листы справки налоговой", form.getTaxCertificatePages()))
                .append(li("Листы справки о судимости", form.getCriminalRecordPages()))
                .append(li("Листы отправки кредиторам", form.getCreditorPostingProofPages()))
                .append("</ul></section>");

        html.append("<section><h2>Вспомогательные блоки перед генерацией DOCX</h2>")
                .append("<p><strong>Источник:</strong> ")
                .append(auxiliaryTextBlocks.generatedByOpenAi() ? "OpenAI API" : "Пользовательский/стандартный fallback")
                .append("</p><p><strong>Причина:</strong> ").append(escape(auxiliaryTextBlocks.hardshipReason()))
                .append("</p><p><strong>Работа/доходы:</strong> ").append(escape(auxiliaryTextBlocks.employmentIncomeDescription()))
                .append("</p><p><strong>Использование займа:</strong> ").append(escape(auxiliaryTextBlocks.loanFundsUsageDescription()))
                .append("</p></section>");

        html.append("<form class=\"action\" method=\"post\" action=\"/generate\"><button type=\"submit\">Сформировать ZIP с DOCX-документами</button></form>")
                .append("<p><a href='/'>← Назад к форме</a></p></body></html>");
        return html.toString();
    }

    private BankruptcyApplicationData buildApplicationData(PreviewFormData form,
                                                           List<Creditor> creditors,
                                                           List<RealEstateItem> realEstateItems,
                                                           List<Vehicle> vehicles,
                                                           List<Child> children) {
        Address registration = new Address(
                "Россия",
                safe(form.getRegistrationRegion()),
                safe(form.getRegistrationCity()),
                safe(form.getRegistrationStreet()),
                safe(form.getRegistrationHouse() + " " + form.getRegistrationBuilding()).trim(),
                safe(form.getRegistrationApartment()),
                safe(form.getRegistrationPostalCode())
        );
        Debtor debtor = new Debtor(
                form.getFullName().isBlank() ? "-" : form.getFullName(),
                parseDate(form.getBirthDate()),
                safe(form.getSnils()),
                safe(form.getInn()),
                (safe(form.getPassportSeries()) + " " + safe(form.getPassportNumber())).trim(),
                registration,
                registration,
                "",
                "",
                safe(form.getBirthPlace())
        );
        EmploymentInfo employmentInfo = new EmploymentInfo(
                safe(form.getEmploymentStatus()),
                safe(form.getEmployerName()),
                safe(form.getPosition()),
                parseAmount(form.getMonthlyIncome())
        );
        PropertyInfo propertyInfo = new PropertyInfo(vehicles, realEstateItems, false);
        FamilyInfo familyInfo = new FamilyInfo(
                "MARRIED".equalsIgnoreCase(safe(form.getMaritalStatus())),
                familyDescriptor(form),
                children
        );
        return new BankruptcyApplicationData(debtor, creditors, familyInfo, employmentInfo, propertyInfo);
    }

    private String familyDescriptor(PreviewFormData form) {
        String status = safe(form.getMaritalStatus()).toUpperCase();
        return switch (status) {
            case "MARRIED" -> safe(form.getSpouseName()) + ", дата брака: " + safe(form.getMarriageDate())
                    + ", свидетельство: " + safe(form.getMarriageCertificate());
            case "DIVORCED" -> "дата расторжения брака: " + safe(form.getDivorceDate())
                    + ", свидетельство о расторжении брака: " + safe(form.getDivorceCertificate());
            case "WIDOWED" -> safe(form.getSpouseName()) + ", дата смерти: " + safe(form.getSpouseDeathDate())
                    + ", свидетельство о смерти: " + safe(form.getDeathCertificate());
            default -> "";
        };
    }

    private List<String> buildWarnings(PreviewFormData form) {
        String status = safe(form.getMaritalStatus()).toUpperCase();
        List<String> warnings = new ArrayList<>();
        boolean spouseFieldsFilled = !safe(form.getSpouseName()).isBlank()
                || !safe(form.getMarriageDate()).isBlank()
                || !safe(form.getMarriageCertificate()).isBlank()
                || !safe(form.getDivorceDate()).isBlank()
                || !safe(form.getDivorceCertificate()).isBlank()
                || !safe(form.getSpouseDeathDate()).isBlank()
                || !safe(form.getDeathCertificate()).isBlank();
        if ("SINGLE".equals(status) && spouseFieldsFilled) {
            warnings.add("maritalStatus = SINGLE, но заполнены данные супруга — эти данные будут проигнорированы.");
        }
        return warnings;
    }

    private String buildFamilyBlock(PreviewFormData form, List<Child> children) {
        String status = safe(form.getMaritalStatus()).toUpperCase();
        StringBuilder block = new StringBuilder();
        switch (status) {
            case "MARRIED" -> block.append("Состоит в зарегистрированном браке");
            case "DIVORCED" -> block.append("Брак расторгнут");
            case "WIDOWED" -> block.append("Супруг(а) умер(ла)");
            default -> block.append("В браке не состоит.");
        }
        if ("MARRIED".equals(status)) {
            block.append(" с ").append(safe(form.getSpouseName()))
                    .append(", дата регистрации брака: ").append(formatDate(form.getMarriageDate()))
                    .append(", свидетельство о заключении брака: ").append(safe(form.getMarriageCertificate())).append(".");
        }
        if ("DIVORCED".equals(status)) {
            block.append(", дата расторжения брака: ").append(formatDate(form.getDivorceDate()))
                    .append(", свидетельство о расторжении брака: ").append(safe(form.getDivorceCertificate())).append(".");
        }
        if ("WIDOWED".equals(status)) {
            block.append(" ").append(safe(form.getSpouseName()))
                    .append(", дата смерти: ").append(formatDate(form.getSpouseDeathDate()))
                    .append(", свидетельство о смерти: ").append(safe(form.getDeathCertificate())).append(".");
        }
        block.append(children.isEmpty() ? " Несовершеннолетних детей на иждивении не имеет." : " Несовершеннолетние дети указаны.");
        return block.toString();
    }

    private String buildPropertyBlock(String realEstateLines, String vehicleLines) {
        StringBuilder block = new StringBuilder();
        List<String[]> realEstateItems = previewInputParser.parseStructuredLines(realEstateLines, 7);
        List<String[]> vehicles = previewInputParser.parseStructuredLines(vehicleLines, 11);
        block.append(realEstateItems.isEmpty() ? "Объекты недвижимости в собственности отсутствуют." : "Недвижимость указана.")
                .append(System.lineSeparator())
                .append(vehicles.isEmpty() ? "Транспортные средства отсутствуют." : "Транспортные средства указаны.");
        return block.toString();
    }

    private String buildEmploymentBlock(String employmentStatus) {
        String normalizedStatus = employmentStatus == null ? "" : employmentStatus.trim().toUpperCase();
        return switch (normalizedStatus) {
            case "UNEMPLOYED" -> "Официального места работы не имеет.";
            case "EMPLOYED" -> "Официально трудоустроен.";
            case "INFORMAL_WORK" -> "Неформальная занятость.";
            case "PENSIONER" -> "Пенсионер.";
            case "OTHER" -> "Иной статус занятости.";
            default -> "Сведения о занятости не предоставлены.";
        };
    }

    private void appendCreditorDebtBlock(StringBuilder html, Creditor creditor) {
        BigDecimal creditorTotal = debtCalculationService.calculateCreditorTotal(creditor);
        html.append("<article><h3>Задолженность перед ")
                .append(escape(creditor.name()))
                .append(" (общая сумма: ")
                .append(debtCalculationService.formatAmountRu(creditorTotal))
                .append(" ₽)</h3><ul>");
        for (Contract contract : creditor.contracts()) {
            html.append("<li>")
                    .append(escape(contract.contractNumber()))
                    .append(" — ")
                    .append(debtCalculationService.formatAmountRu(contract.principalDebt()))
                    .append(" ₽</li>");
        }
        html.append("</ul></article>");
    }

    private String li(String key, String value) {
        return "<li><strong>" + escape(key) + ":</strong> " + escape(value == null || value.isBlank() ? "-" : value) + "</li>";
    }

    private String effectiveAddress(PreviewFormData form) {
        if (!safe(form.getRegistrationFullAddress()).isBlank()) {
            return form.getRegistrationFullAddress();
        }
        return String.join(", ", safe(form.getRegistrationPostalCode()), safe(form.getRegistrationRegion()),
                safe(form.getRegistrationDistrict()), safe(form.getRegistrationCity()), safe(form.getRegistrationSettlement()),
                safe(form.getRegistrationStreet()), safe(form.getRegistrationHouse()), safe(form.getRegistrationBuilding()), safe(form.getRegistrationApartment()))
                .replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
    }

    private BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(safe(value).replace(" ", "").replace(',', '.'));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String dateValue) {
        if (dateValue == null || dateValue.isBlank()) {
            return LocalDate.of(1989, 3, 14);
        }
        try {
            return LocalDate.parse(dateValue.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return LocalDate.of(1989, 3, 14);
        }
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String escape(String text) {
        return (text == null ? "" : text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
