package com.bankrotapp.controller;

import com.bankrotapp.model.BankruptcyApplicationData;
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
import java.util.List;

@RestController
public class DocumentGenerationController {

    public static final String SESSION_PREVIEW_DATA = "previewData";

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
    public ResponseEntity<byte[]> generateDocumentsArchive(@RequestBody(required = false) BankruptcyApplicationData request,
                                                           HttpSession session) throws IOException {
        BankruptcyApplicationData data = normalizeData(resolveData(request, session));
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
        BankruptcyApplicationData data = normalizeData(resolveData(request, session));
        byte[] generated = documentGenerationService.generateAppendixOneDocx(data, data.creditors());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-1-" + sanitizeFileName(data.debtor().fullName()) + ".docx").build());

        return ResponseEntity.ok().headers(headers).body(generated);
    }

    @PostMapping(value = "/generate/appendix-2", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> generatePropertyInventory(@RequestBody(required = false) BankruptcyApplicationData request,
                                                            HttpSession session) throws IOException {
        BankruptcyApplicationData data = normalizeData(resolveData(request, session));
        byte[] generated = documentGenerationService.generateAppendixTwoDocx(data, data.propertyInfo().realEstateItems(), data.propertyInfo().vehicles(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("prilozhenie-2-" + sanitizeFileName(data.debtor().fullName()) + ".docx").build());

        return ResponseEntity.ok().headers(headers).body(generated);
    }

    private BankruptcyApplicationData resolveData(BankruptcyApplicationData request, HttpSession session) {
        if (request != null) {
            return request;
        }
        Object fromSession = session.getAttribute(SESSION_PREVIEW_DATA);
        if (fromSession instanceof BankruptcyApplicationData bankruptcyApplicationData) {
            return bankruptcyApplicationData;
        }
        throw new MissingPreviewSessionDataException();
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

    private static class MissingPreviewSessionDataException extends RuntimeException {
    }
}
