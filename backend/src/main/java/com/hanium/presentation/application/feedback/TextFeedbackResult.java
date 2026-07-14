package com.hanium.presentation.application.feedback;

import java.util.LinkedHashMap;
import java.util.Map;

public record TextFeedbackResult(
        Map<String, Object> payload,
        String provider,
        String model,
        boolean fallback,
        String fallbackReason
) {
    public static TextFeedbackResult success(Map<String, Object> payload, String provider, String model) {
        return new TextFeedbackResult(payload, provider, model, false, null);
    }

    public static TextFeedbackResult fallback(Map<String, Object> payload, String provider, String model, String reason) {
        return new TextFeedbackResult(payload, provider, model, true, reason);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider);
        metadata.put("model", model);
        metadata.put("fallback", fallback);
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            metadata.put("fallbackReason", fallbackReason);
        }
        return metadata;
    }
}
