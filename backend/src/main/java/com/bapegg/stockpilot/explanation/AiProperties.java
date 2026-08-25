package com.bapegg.stockpilot.explanation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stockpilot.ai")
public record AiProperties(boolean enabled, String provider, String baseUrl, String apiKey, String model) {

    public AiProperties {
        provider = provider == null ? "" : provider;
        baseUrl = baseUrl == null ? "" : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
        model = model == null ? "" : model;
    }

    public boolean fullyConfigured() {
        return !provider.isBlank() && !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }
}
