package com.bankrotapp.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Проверяет, что в DOCX-шаблонах нет запрещённых legacy-строк.
 *
 * Запуск:
 * mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool
 */
public final class TemplateAuditTool {

    private TemplateAuditTool() {
    }

    public static void main(String[] args) throws Exception {
        Path templatesDir = TemplatePaths.resolveTemplatesDir(args);
        List<String> violations = new ArrayList<>();

        for (String fileName : TemplatePaths.TEMPLATE_FILES) {
            Path templateFile = templatesDir.resolve(fileName);
            if (!Files.exists(templateFile)) {
                violations.add(fileName + ": файл не найден");
                continue;
            }

            String xml = readWordXml(templateFile);
            for (String marker : TemplatePaths.FORBIDDEN_MARKERS) {
                if (xml.contains(marker)) {
                    violations.add(fileName + ": найден запрещённый маркер '" + marker + "'");
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("Template audit failed:\n - " + String.join("\n - ", violations));
        }

        System.out.println("Template audit passed: запрещённые маркеры не найдены.");
    }

    private static String readWordXml(Path templateFile) throws IOException {
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
