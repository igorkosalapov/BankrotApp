package com.bankrotapp.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanupToolShouldCreateAllTemplatesAndAuditShouldPass() throws Exception {
        TemplateCleanupTool.main(new String[]{tempDir.toString()});
        TemplateAuditTool.main(new String[]{tempDir.toString()});

        for (String fileName : TemplatePaths.TEMPLATE_FILES) {
            assertTrue(Files.exists(tempDir.resolve(fileName)), "Не создан шаблон: " + fileName);
        }

        String statementXml = readWordXml(tempDir.resolve("zayavlenie.docx"));
        assertTrue(statementXml.contains("{{debtor.fullName}}"));
        assertTrue(statementXml.contains("{{totalDebtFormatted}}"));

        for (String marker : TemplatePaths.FORBIDDEN_MARKERS) {
            assertFalse(statementXml.contains(marker), "В шаблоне найден запрещенный маркер: " + marker);
        }
    }

    @Test
    void appendixTemplatesShouldKeepExpectedTableStructure() throws Exception {
        TemplateCleanupTool.main(new String[]{tempDir.toString()});

        try (XWPFDocument appendixOne = open(tempDir.resolve("prilozhenie_1.docx"));
             XWPFDocument appendixTwo = open(tempDir.resolve("prilozhenie_2.docx"))) {
            assertEquals(2, appendixOne.getTables().size(), "Приложение №1 должно содержать 2 таблицы.");
            assertTrue(appendixOne.getTables().get(0).getNumberOfRows() > 20, "Таблица данных должника (Прил.1) должна содержать >=21 строку.");
            assertTrue(appendixOne.getTables().get(1).getNumberOfRows() > 4, "Таблица кредиторов (Прил.1) должна содержать >=5 строк.");

            assertEquals(4, appendixTwo.getTables().size(), "Приложение №2 должно содержать 4 таблицы.");
            assertTrue(appendixTwo.getTables().get(0).getNumberOfRows() > 20, "Таблица данных должника (Прил.2) должна содержать >=21 строку.");
            assertTrue(appendixTwo.getTables().get(1).getNumberOfRows() > 10, "Таблица недвижимости должна содержать >=11 строк.");
            assertTrue(appendixTwo.getTables().get(2).getNumberOfRows() > 15, "Таблица ТС должна содержать >=16 строк.");
            assertTrue(appendixTwo.getTables().get(3).getNumberOfRows() > 6, "Таблица счетов должна содержать >=7 строк.");
        }
    }

    @Test
    void testTemplatesDoNotContainRealSampleData() throws Exception {
        TemplateCleanupTool.main(new String[]{tempDir.toString()});

        for (String fileName : TemplatePaths.TEMPLATE_FILES) {
            String xml = readWordXml(tempDir.resolve(fileName));
            for (String marker : TemplatePaths.FORBIDDEN_MARKERS) {
                assertFalse(xml.contains(marker), "Шаблон " + fileName + " содержит запрещённую строку: " + marker);
            }
        }
    }

    private XWPFDocument open(Path path) throws IOException {
        return new XWPFDocument(Files.newInputStream(path));
    }

    private String readWordXml(Path templateFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(templateFile);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                    builder.append(new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return builder.toString();
    }
}
