package com.bankrotapp.template;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Генерирует чистые DOCX-шаблоны в текстово-управляемом режиме.
 *
 * Запуск:
 * mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool
 */
public final class TemplateCleanupTool {

    private TemplateCleanupTool() {
    }

    public static void main(String[] args) throws Exception {
        Path templatesDir = TemplatePaths.resolveTemplatesDir(args);
        Files.createDirectories(templatesDir);

        writeStatementTemplate(templatesDir.resolve("zayavlenie.docx"));
        writeAppendixOneTemplate(templatesDir.resolve("prilozhenie_1.docx"));
        writeAppendixTwoTemplate(templatesDir.resolve("prilozhenie_2.docx"));

        System.out.println("Готово: шаблоны пересозданы в " + templatesDir.toAbsolutePath());
    }

    private static void writeStatementTemplate(Path targetFile) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            addHeading(document, "ЗАЯВЛЕНИЕ О ПРИЗНАНИИ ГРАЖДАНИНА БАНКРОТОМ");
            addLine(document, "Должник: {{debtor.fullName}}");
            addLine(document, "Краткое ФИО: {{debtor.shortName}}");
            addLine(document, "Дата рождения (текстом): {{debtor.birthDateText}}");
            addLine(document, "Место рождения: {{debtor.birthPlace}}");
            addLine(document, "Паспорт: {{debtor.passportFull}}");
            addLine(document, "Адрес регистрации: {{debtor.registrationAddress.fullAddress}}");
            addLine(document, "ИНН: {{debtor.inn}}");
            addLine(document, "СНИЛС: {{debtor.snils}}");
            addLine(document, "\nСведения по обязательствам: {{creditorsBlock}}");
            addLine(document, "Общая сумма задолженности: {{totalDebtFormatted}}");
            addLine(document, "Недвижимость: {{realEstateBlock}}");
            addLine(document, "Транспорт: {{vehicleBlock}}");
            addLine(document, "Семейное положение: {{familyBlock}}");
            addLine(document, "Трудовая занятость: {{employmentBlock}}");
            addLine(document, "Приложения: {{attachmentsBlock}}");
            addLine(document, "\nПодпись: {{signatureFullName}}");
            writeDoc(document, targetFile);
        }
    }

    private static void writeAppendixOneTemplate(Path targetFile) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            addHeading(document, "Приложение №1. Список кредиторов");

            XWPFTable debtorInfo = createTable(document, 21, 3);
            for (int rowIndex = 0; rowIndex <= 20; rowIndex++) {
                XWPFTableRow row = debtorInfo.getRow(rowIndex);
                row.getCell(0).setText("Поле " + rowIndex);
                row.getCell(1).setText(":");
                row.getCell(2).setText("-");
            }
            debtorInfo.getRow(1).getCell(0).setText("Фамилия");
            debtorInfo.getRow(2).getCell(0).setText("Имя");
            debtorInfo.getRow(3).getCell(0).setText("Отчество");
            debtorInfo.getRow(5).getCell(0).setText("Дата рождения");
            debtorInfo.getRow(6).getCell(0).setText("Место рождения");
            debtorInfo.getRow(7).getCell(0).setText("СНИЛС");
            debtorInfo.getRow(8).getCell(0).setText("ИНН");
            debtorInfo.getRow(11).getCell(0).setText("Паспорт");
            debtorInfo.getRow(13).getCell(0).setText("Регион");
            debtorInfo.getRow(15).getCell(0).setText("Город");
            debtorInfo.getRow(17).getCell(0).setText("Улица");
            debtorInfo.getRow(18).getCell(0).setText("Дом");
            debtorInfo.getRow(20).getCell(0).setText("Квартира");

            addLine(document, "");
            XWPFTable creditors = createTable(document, 9, 8);
            creditors.getRow(0).getCell(0).setText("№");
            creditors.getRow(0).getCell(1).setText("Содержание обязательства");
            creditors.getRow(0).getCell(2).setText("Кредитор");
            creditors.getRow(0).getCell(3).setText("Адрес кредитора");
            creditors.getRow(0).getCell(4).setText("Основание");
            creditors.getRow(0).getCell(5).setText("Общая сумма");
            creditors.getRow(0).getCell(6).setText("Сумма долга");
            creditors.getRow(0).getCell(7).setText("Штрафы");
            for (int rowIndex = 1; rowIndex < 9; rowIndex++) {
                for (int col = 0; col < 8; col++) {
                    creditors.getRow(rowIndex).getCell(col).setText(col == 0 ? "1." + rowIndex : "-");
                }
            }

            writeDoc(document, targetFile);
        }
    }

    private static void writeAppendixTwoTemplate(Path targetFile) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            addHeading(document, "Приложение №2. Опись имущества");

            XWPFTable debtorInfo = createTable(document, 21, 3);
            for (int rowIndex = 0; rowIndex <= 20; rowIndex++) {
                XWPFTableRow row = debtorInfo.getRow(rowIndex);
                row.getCell(0).setText("Поле " + rowIndex);
                row.getCell(1).setText(":");
                row.getCell(2).setText("-");
            }

            addLine(document, "");
            XWPFTable realEstate = createTable(document, 11, 7);
            realEstate.getRow(0).getCell(0).setText("Категория");
            realEstate.getRow(0).getCell(2).setText("Вид права");
            realEstate.getRow(0).getCell(3).setText("Адрес");
            realEstate.getRow(0).getCell(4).setText("Площадь");
            realEstate.getRow(0).getCell(5).setText("Основание");
            realEstate.getRow(0).getCell(6).setText("Залог");
            String[] categories = {"", "", "земельные участки", "дома", "квартиры", "квартиры-2", "иное"};
            for (int row = 1; row <= 10; row++) {
                realEstate.getRow(row).getCell(1).setText(row < categories.length ? categories[row] : "-");
                for (int col = 2; col <= 6; col++) {
                    realEstate.getRow(row).getCell(col).setText("-");
                }
            }

            addLine(document, "");
            XWPFTable vehicles = createTable(document, 16, 7);
            vehicles.getRow(0).getCell(1).setText("Вид транспорта");
            vehicles.getRow(0).getCell(2).setText("Рег. знак");
            vehicles.getRow(0).getCell(3).setText("Собственность");
            vehicles.getRow(0).getCell(4).setText("Место нахождения");
            vehicles.getRow(0).getCell(5).setText("Документы");
            vehicles.getRow(0).getCell(6).setText("Залог");
            int categoryNo = 1;
            for (int row = 1; row <= 15; row++) {
                vehicles.getRow(row).getCell(1).setText(categoryNo + ")");
                for (int col = 2; col <= 6; col++) {
                    vehicles.getRow(row).getCell(col).setText("-");
                }
                categoryNo++;
            }

            addLine(document, "");
            XWPFTable accounts = createTable(document, 7, 5);
            accounts.getRow(0).getCell(1).setText("Банк");
            accounts.getRow(0).getCell(2).setText("Счет");
            accounts.getRow(0).getCell(3).setText("Дата открытия");
            accounts.getRow(0).getCell(4).setText("Остаток");
            for (int row = 1; row <= 6; row++) {
                for (int col = 1; col <= 4; col++) {
                    accounts.getRow(row).getCell(col).setText("-");
                }
            }

            writeDoc(document, targetFile);
        }
    }

    private static XWPFTable createTable(XWPFDocument document, int rows, int cols) {
        XWPFTable table = document.createTable(rows, cols);
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            for (int colIndex = 0; colIndex < cols; colIndex++) {
                row.getCell(colIndex).removeParagraph(0);
                row.getCell(colIndex).addParagraph();
            }
        }
        return table;
    }

    private static void addHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setText(text);
    }

    private static void addLine(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.createRun().setText(text);
    }

    private static void writeDoc(XWPFDocument document, Path targetFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(targetFile)) {
            document.write(outputStream);
        }
    }
}
