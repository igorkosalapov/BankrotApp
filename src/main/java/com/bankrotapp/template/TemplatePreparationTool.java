package com.bankrotapp.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Подготавливает DOCX-шаблоны из raw-версий в памяти
 * (или по запросу пишет в target/generated-docx-templates).
 */
public final class TemplatePreparationTool {

    public static final List<String> REQUIRED_STATEMENT_MARKERS = List.of(
            "{{headerBlock}}",
            "{{debtorIntroBlock}}",
            "{{creditorsDebtBlock}}",
            "{{familyBlock}}",
            "{{vehicleBlock}}",
            "{{attachmentsBlock}}",
            "{{signatureFullName}}"
    );

    private TemplatePreparationTool() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = Path.of("target", "generated-docx-templates");
        Files.createDirectories(outputDir);

        byte[] rawStatement = readClasspath("templates/zayavlenie.docx");
        byte[] preparedStatement = prepareStatementTemplate(rawStatement);
        Files.write(outputDir.resolve("zayavlenie.docx"), preparedStatement);

        Files.write(outputDir.resolve("prilozhenie_1.docx"), readClasspath("templates/prilozhenie_1.docx"));
        Files.write(outputDir.resolve("prilozhenie_2.docx"), readClasspath("templates/prilozhenie_2.docx"));

        System.out.println("Prepared templates written to " + outputDir.toAbsolutePath());
    }

    public static byte[] prepareStatementTemplate(byte[] rawTemplateBytes) throws IOException {
        Map<String, byte[]> entries = unzip(rawTemplateBytes);
        String documentXml = new String(entries.getOrDefault("word/document.xml", new byte[0]));
        if (documentXml.isBlank()) {
            return rawTemplateBytes;
        }

        for (String marker : REQUIRED_STATEMENT_MARKERS) {
            if (!documentXml.contains(marker)) {
                String paragraph = "<w:p><w:r><w:t>" + marker + "</w:t></w:r></w:p>";
                documentXml = documentXml.replace("</w:body>", paragraph + "</w:body>");
            }
        }

        entries.put("word/document.xml", documentXml.getBytes());
        return zip(entries);
    }

    private static byte[] readClasspath(String path) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + path);
            }
            return inputStream.readAllBytes();
        }
    }

    private static Map<String, byte[]> unzip(byte[] docxBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(docxBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), zipInputStream.readAllBytes());
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return out.toByteArray();
        }
    }
}
