package com.bankrotapp.document;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DocxTemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}");

    public byte[] render(InputStream templateStream, Map<String, String> placeholders) throws IOException {
        try (XWPFDocument document = new XWPFDocument(templateStream)) {
            replaceInParagraphs(document.getParagraphs(), placeholders);
            replaceInTables(document.getTables(), placeholders);

            for (XWPFHeader header : document.getHeaderList()) {
                replaceInParagraphs(header.getParagraphs(), placeholders);
                replaceInTables(header.getTables(), placeholders);
            }

            for (XWPFFooter footer : document.getFooterList()) {
                replaceInParagraphs(footer.getParagraphs(), placeholders);
                replaceInTables(footer.getTables(), placeholders);
            }

            ensureNoPlaceholdersLeft(document);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }

    private void replaceInTables(List<XWPFTable> tables, Map<String, String> placeholders) {
        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceInParagraphs(cell.getParagraphs(), placeholders);
                    replaceInTables(cell.getTables(), placeholders);
                }
            }
        }
    }

    private void replaceInParagraphs(List<XWPFParagraph> paragraphs, Map<String, String> placeholders) {
        for (XWPFParagraph paragraph : paragraphs) {
            String text = paragraph.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }

            String replacedText = replaceText(text, placeholders);
            if (!text.equals(replacedText)) {
                int runCount = paragraph.getRuns().size();
                for (int i = runCount - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }
                XWPFRun run = paragraph.createRun();
                run.setText(replacedText);
            }
        }
    }

    private String replaceText(String source, Map<String, String> placeholders) {
        String result = source;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private void ensureNoPlaceholdersLeft(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            String allText = extractText(out.toByteArray());
            if (PLACEHOLDER_PATTERN.matcher(allText).find()) {
                throw new IllegalStateException("После генерации в DOCX остались placeholders вида {{...}}.");
            }
        }
    }

    private String extractText(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
