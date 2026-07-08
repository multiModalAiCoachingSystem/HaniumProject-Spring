// 역할: 백엔드의 TextLlmProperties 기능을 담당하는 Java 코드입니다.
package com.hanium.presentation.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.text-llm")
public class TextLlmProperties {
    private String provider;
    private String baseUrl;
    private String model;

    // 함수 역할: getProvider에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getProvider() {
        return provider;
    }

    // 함수 역할: setProvider에서 대상 객체의 상태를 갱신하고 저장 흐름에 반영합니다.
    public void setProvider(String provider) {
        this.provider = provider;
    }

    // 함수 역할: getBaseUrl에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getBaseUrl() {
        return baseUrl;
    }

    // 함수 역할: setBaseUrl에서 대상 객체의 상태를 갱신하고 저장 흐름에 반영합니다.
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // 함수 역할: getModel에서 필요한 값이나 상태를 조회해 반환합니다.
    public String getModel() {
        return model;
    }

    // 함수 역할: setModel에서 대상 객체의 상태를 갱신하고 저장 흐름에 반영합니다.
    public void setModel(String model) {
        this.model = model;
    }
}
