package com.hanium.presentation.infrastructure.client.textllm;

import com.hanium.presentation.global.properties.TextLlmProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@SuppressWarnings("null")
public class OllamaTextLlmClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final TextLlmProperties properties;

    public OllamaTextLlmClient(WebClient webClient, TextLlmProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String generate(String prompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("prompt", prompt);
        request.put("stream", false);
        request.put("format", "json");
        request.put("options", Map.of(
                "temperature", 0.2,
                "num_ctx", 8192
        ));

        Map<String, Object> response = webClient.post()
                .uri(properties.getBaseUrl() + "/api/generate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MAP_RESPONSE)
                .block(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));

        if (response == null || response.get("response") == null) {
            throw new IllegalStateException("Ollama returned empty response");
        }

        String generatedText = response.get("response").toString().trim();
        if (generatedText.isBlank()) {
            throw new IllegalStateException("Ollama returned blank response");
        }
        return generatedText;
    }
}
