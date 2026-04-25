package com.bankrotapp.controller;

import com.bankrotapp.model.BankruptcyApplicationData;
import com.bankrotapp.model.Contract;
import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Debtor;
import com.bankrotapp.model.EmploymentInfo;
import com.bankrotapp.model.PropertyInfo;
import com.bankrotapp.model.RealEstateItem;
import com.bankrotapp.model.Vehicle;
import com.bankrotapp.service.DocumentGenerationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
public class DocumentGenerationController {

    public static final String SESSION_PREVIEW_DATA = "bankruptcyApplicationData";

    private final DocumentGenerationService documentGenerationService;

    public DocumentGenerationController(DocumentGenerationService documentGenerationService) {
        this.documentGenerationService = documentGenerationService;
    }


    @org.springframework.web.bind.annotation.ExceptionHandler(MissingPreviewSessionDataException.class)
    public ResponseEntity<Void> handleMissingPreviewSessionData() {
        return ResponseEntity.status(303)
                .header(HttpHeaders.LOCATION, "/?error=Сначала+сформируйте+предпросмотр+и+повторите+генерацию")
                .build();
    }

    @PostMapping(value = "/generate", produces = "application/zip")
    public ResponseEntity<byte[]> generateDocumentsArchive(HttpSession session) throws IOException {
        BankruptcyApplicationData data = normalizeData(resolveRequiredData(session));
        byte[] zip = documentGenerationService.generateZip(data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("dokumenty-" + sanitizeFileName(data.debtor().fullName()) + ".zip").build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(zip);
    }

    @PostMapping(value = "/generate/appendix-1", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generateAppendixOne(@RequestBody(required = false) BankruptcyApplicationData request,
                                                      HttpSession session) throws IOException {
        BankruptcyApplicationData data = normalizeData(resolveDataWithDefaults(request, session));
        byte[] generated = documentGenerationService.generateAppendixOneDocx(data, data.creditors());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-1-" + sanitizeFileName(data.debtor().fullName()) + ".docx").build());

        return ResponseEntity.ok().headers(headers).body(generated);
    }

    @PostMapping(value = "/generate/appendix-2", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generatePropertyInventory(@RequestBody(required = false) BankruptcyApplicationData request,
                                                            HttpSession session) throws IOException {
        BankruptcyApplicationData data = normalizeData(resolveDataWithDefaults(request, session));
        byte[] generated = documentGenerationService.generateAppendixTwoDocx(data, data.propertyInfo().realEstateItems(), data.propertyInfo().vehicles(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-2-" + sanitizeFileName(data.debtor().fullName()) + ".docx").build());

        return ResponseEntity.ok().headers(headers).body(generated);
    }

    private BankruptcyApplicationData resolveRequiredData(HttpSession session) {
        Object fromSession = session.getAttribute(SESSION_PREVIEW_DATA);
        if (fromSession instanceof BankruptcyApplicationData bankruptcyApplicationData) {
            return bankruptcyApplicationData;
        }
        throw new MissingPreviewSessionDataException();
    }

    private BankruptcyApplicationData resolveDataWithDefaults(BankruptcyApplicationData request, HttpSession session) {
        if (request != null) {
            return request;
        }
        Object fromSession = session.getAttribute(SESSION_PREVIEW_DATA);
        if (fromSession instanceof BankruptcyApplicationData bankruptcyApplicationData) {
            return bankruptcyApplicationData;
        }
        return defaultData();
    }

    private String sanitizeFileName(String value) {
        return safe(value).replaceAll("[\\\\/:*?\"<>|]", "_").replace(' ', '_');
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private BankruptcyApplicationData normalizeData(BankruptcyApplicationData request) {
        Debtor debtor = request.debtor();
        List<Creditor> creditors = request.creditors() == null ? List.of() : request.creditors();
        PropertyInfo propertyInfo = request.propertyInfo() == null ? new PropertyInfo(List.of(), List.of(), false) : request.propertyInfo();
        EmploymentInfo employmentInfo = request.employmentInfo() == null ? new EmploymentInfo("", "", "", BigDecimal.ZERO) : request.employmentInfo();

        if (debtor == null) {
            throw new MissingPreviewSessionDataException();
        }

        List<Vehicle> vehicles = propertyInfo.vehicles() == null ? List.of() : propertyInfo.vehicles();
        List<RealEstateItem> realEstateItems = propertyInfo.realEstateItems() == null ? List.of() : propertyInfo.realEstateItems();
        PropertyInfo normalizedPropertyInfo = new PropertyInfo(vehicles, realEstateItems, propertyInfo.hasOtherValuableProperty());

        return new BankruptcyApplicationData(debtor, creditors, request.familyInfo(), employmentInfo, normalizedPropertyInfo);
    }

    private BankruptcyApplicationData defaultData() {
        Debtor debtor = new Debtor(
                "Иванов Иван Иванович",
                LocalDate.of(1989, 3, 14),
                "112-233-445 95",
                "770123456789",
                "4510 123456",
                new com.bankrotapp.model.Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                new com.bankrotapp.model.Address("Россия", "г. Москва", "Москва", "Тверская", "10", "15", "125009"),
                "79161234567",
                "ivanov@example.com",
                "г. Москва"
        );

        List<Creditor> creditors = List.of(
                new Creditor(
                        "Банк А",
                        "7700000000",
                        List.of(new Contract("Кредитный договор № 1 от 10.01.2024", "loan", new BigDecimal("150000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                ),
                new Creditor(
                        "МФО Б",
                        "7800000000",
                        List.of(new Contract("Договор займа № 77 от 03.02.2025", "microloan", new BigDecimal("25000.00"), BigDecimal.ZERO, BigDecimal.ZERO))
                )
        );

        PropertyInfo propertyInfo = new PropertyInfo(
                List.of(new Vehicle("легковой автомобиль", "Hyundai", "Solaris", "X7LBR32AAB1234567", 2017)),
                List.of(new RealEstateItem("квартира", debtor.registrationAddress(), 62.4, "Собственность")),
                false
        );

        EmploymentInfo employmentInfo = new EmploymentInfo("EMPLOYED", "ООО Пример", "Специалист", new BigDecimal("80000"));
        return new BankruptcyApplicationData(debtor, creditors, null, employmentInfo, propertyInfo);
    }

    private static class MissingPreviewSessionDataException extends RuntimeException {
    }
}
