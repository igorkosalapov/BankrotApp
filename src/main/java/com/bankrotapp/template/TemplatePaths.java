package com.bankrotapp.template;

import java.nio.file.Path;
import java.util.List;

final class TemplatePaths {

    static final List<String> TEMPLATE_FILES = List.of(
            "zayavlenie.docx",
            "prilozhenie_1.docx",
            "prilozhenie_2.docx"
    );

    static final List<String> FORBIDDEN_MARKERS = List.of(
            "Захаров",
            "ВЭББАНКИР",
            "ТУРБОЗАЙМ",
            "МИГКРЕДИТ",
            "MITSUBISHI RVR",
            "1 248 887,93"
    );

    private TemplatePaths() {
    }

    static Path resolveTemplatesDir(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]);
        }
        return Path.of("src", "main", "resources", "templates");
    }
}
