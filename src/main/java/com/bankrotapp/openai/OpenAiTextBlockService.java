package com.bankrotapp.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class OpenAiTextBlockService {

    private static final String DEFAULT_HARDSHIP_REASON = "Должник оказался в тяжелом финансовом положении в связи со снижением доступного дохода и увеличением обязательных расходов.";
    private static final String DEFAULT_EMPLOYMENT_INCOME = "Должник официально трудоустроен и получает регулярный доход, однако его недостаточно для своевременного погашения всех обязательств.";
    private static final String DEFAULT_LOAN_USAGE = "Заемные средства были использованы на повседневные нужды семьи, обязательные расходы и частичное исполнение ранее принятых обязательств.";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public OpenAiTextBlockService(ObjectMapper objectMapper,
                                  @Value("${openai.api-key:}") String apiKey,
                                  @Value("${openai.model:gpt-4o-mini}") String model,
                                  @Value("${openai.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = apiUrl;
    }

    public OpenAiTextBlocks generateAuxiliaryBlocks(String hardshipReasonInput,
                                                    String employmentIncomeInput,
                                                    String loanFundsUsageInput) {
        OpenAiTextBlocks fallback = buildFallback(hardshipReasonInput, employmentIncomeInput, loanFundsUsageInput);
        if (apiKey == null || apiKey.isBlank()) {
            return fallback;
        }

        try {
            String payload = buildPayload(hardshipReasonInput, employmentIncomeInput, loanFundsUsageInput);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback;
            }

            OpenAiTextBlocks fromModel = parseOpenAiResponse(response.body());
            if (fromModel == null) {
                return fallback;
            }
            return fromModel;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (IOException ex) {
            return fallback;
        }
    }

    private String buildPayload(String hardshipReasonInput,
                                String employmentIncomeInput,
                                String loanFundsUsageInput) throws IOException {
        String userPrompt = "Входные данные пользователя (могут быть пустыми):\n"
                + "reason=" + safe(hardshipReasonInput) + "\n"
                + "employment=" + safe(employmentIncomeInput) + "\n"
                + "loan_usage=" + safe(loanFundsUsageInput) + "\n"
                + "Сформируй только три вспомогательных абзаца для предпросмотра. "
                + "Не добавляй фактов, которых нет во входных данных."
                + " Ответ строго в JSON.";

        JsonNode payload = objectMapper.createObjectNode()
                .put("model", model)
                .put("temperature", 0.2)
                .set("response_format", objectMapper.createObjectNode().put("type", "json_object"))
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "Ты помощник по юридическим документам. "
                                        + "Верни строго JSON с полями hardshipReason, employmentIncomeDescription, loanFundsUsageDescription. "
                                        + "Запрещено выдумывать: ФИО, суммы, даты, кредиторов, имущество, документы, юридические ссылки. "
                                        + "Если данных мало — используй нейтральные формулировки без конкретики."))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", userPrompt)));

        return objectMapper.writeValueAsString(payload);
    }

    private OpenAiTextBlocks parseOpenAiResponse(String responseBody) {
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String content = responseJson.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                return null;
            }

            JsonNode blocks = objectMapper.readTree(content);
            String hardshipReason = safeGenerated(blocks.path("hardshipReason").asText(""), DEFAULT_HARDSHIP_REASON);
            String employmentIncome = safeGenerated(blocks.path("employmentIncomeDescription").asText(""), DEFAULT_EMPLOYMENT_INCOME);
            String loanUsage = safeGenerated(blocks.path("loanFundsUsageDescription").asText(""), DEFAULT_LOAN_USAGE);

            return new OpenAiTextBlocks(hardshipReason, employmentIncome, loanUsage, true);
        } catch (IOException ignored) {
            return null;
        }
    }

    private OpenAiTextBlocks buildFallback(String hardshipReasonInput,
                                           String employmentIncomeInput,
                                           String loanFundsUsageInput) {
        return new OpenAiTextBlocks(
                preferUserOrDefault(hardshipReasonInput, DEFAULT_HARDSHIP_REASON),
                preferUserOrDefault(employmentIncomeInput, DEFAULT_EMPLOYMENT_INCOME),
                preferUserOrDefault(loanFundsUsageInput, DEFAULT_LOAN_USAGE),
                false
        );
    }

    private String safeGenerated(String text, String fallback) {
        String sanitized = safe(text);
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private String preferUserOrDefault(String userValue, String fallback) {
        String normalized = safe(userValue);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
