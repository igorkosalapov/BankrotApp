package com.bankrotapp.controller;

import com.bankrotapp.document.DocxTemplateRenderer;
import com.bankrotapp.model.Address;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.service.DebtCalculationService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
public class DocumentGenerationController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DocxTemplateRenderer docxTemplateRenderer;
    private final DebtCalculationService debtCalculationService;

    public DocumentGenerationController(DocxTemplateRenderer docxTemplateRenderer,
                                        DebtCalculationService debtCalculationService) {
        this.docxTemplateRenderer = docxTemplateRenderer;
        this.debtCalculationService = debtCalculationService;
    }

    @PostMapping(value = "/generate", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generateStatement() throws IOException {
        Debtor debtor = new Debtor(
                "Иванов Иван Иванович",
                LocalDate.of(1989, 3, 14),
                "112-233-445 95",
                "770123456789",
                "4510 123456",
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                new Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                "79161234567",
                "ivanov@example.com",
                "г. Москва"
        );

        List<Creditor> creditors = List.of(
                new Creditor(
                        "Банк А",
                        "7700000000",
                        List.of(new Contract("Кредитный договор № 1", "loan", new BigDecimal("150000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                ),
                new Creditor(
                        "МФО Б",
                        "7800000000",
                        List.of(new Contract("Договор займа № 77", "microloan", new BigDecimal("25000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                )
        );

        BigDecimal totalDebt = debtCalculationService.calculateTotalDebt(creditors);

        Map<String, String> placeholders = Map.of(
                "debtor.fullName", debtor.fullName(),
                "debtor.birthDate", debtor.birthDate().format(DATE_FORMATTER),
                "debtor.birthPlace", debtor.birthPlace(),
                "debtor.registrationAddress.fullAddress", fullAddress(debtor.registrationAddress()),
                "debtor.inn", debtor.inn(),
                "debtor.snils", debtor.snils(),
                "totalDebtFormatted", debtCalculationService.formatAmountRu(totalDebt)
        );

        ClassPathResource template = new ClassPathResource("templates/Zayavlenie_na_bankrotstvo_fiz_litsa_s_pometkami.docx");
        byte[] generated;
        try (InputStream inputStream = template.getInputStream()) {
            generated = docxTemplateRenderer.render(inputStream, placeholders);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("zayavlenie-test.docx").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(generated);
    }

    private String fullAddress(Address address) {
        return String.join(", ",
                safe(address.country()),
                safe(address.region()),
                safe(address.city()),
                safe(address.street()),
                "д. " + safe(address.house()),
                "кв. " + safe(address.apartment()),
                safe(address.postalCode())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
