package com.bankrotapp.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class TemplateXmlUtils {

    private TemplateXmlUtils() {
    }

    static String readWordXml(Path templateFile) throws IOException {
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
