package com.hanium.presentation.global.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {
    private String environment = "development";
    private String baseUrl = "http://localhost";
    private List<String> frontendOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost"
    ));
    private boolean trustProxyHeaders;
    private final Cookie cookie = new Cookie();
    private final RateLimit rateLimit = new RateLimit();

    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> getFrontendOrigins() {
        return frontendOrigins;
    }

    public void setFrontendOrigins(List<String> frontendOrigins) {
        this.frontendOrigins = frontendOrigins;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Cookie {
        private boolean secure;
        private String sameSite = "Lax";

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }
    }

    public static class RateLimit {
        private int loginPerMinute = 10;
        private int registerPerHour = 5;
        private int refreshPerMinute = 20;
        private int uploadPerHour = 20;
        private int analysisPerHour = 20;
        private int retryPerHour = 10;
        private int adminPerMinute = 60;
        private int apiPerMinute = 120;

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getRegisterPerHour() {
            return registerPerHour;
        }

        public void setRegisterPerHour(int registerPerHour) {
            this.registerPerHour = registerPerHour;
        }

        public int getRefreshPerMinute() {
            return refreshPerMinute;
        }

        public void setRefreshPerMinute(int refreshPerMinute) {
            this.refreshPerMinute = refreshPerMinute;
        }

        public int getUploadPerHour() {
            return uploadPerHour;
        }

        public void setUploadPerHour(int uploadPerHour) {
            this.uploadPerHour = uploadPerHour;
        }

        public int getAnalysisPerHour() {
            return analysisPerHour;
        }

        public void setAnalysisPerHour(int analysisPerHour) {
            this.analysisPerHour = analysisPerHour;
        }

        public int getRetryPerHour() {
            return retryPerHour;
        }

        public void setRetryPerHour(int retryPerHour) {
            this.retryPerHour = retryPerHour;
        }

        public int getAdminPerMinute() {
            return adminPerMinute;
        }

        public void setAdminPerMinute(int adminPerMinute) {
            this.adminPerMinute = adminPerMinute;
        }

        public int getApiPerMinute() {
            return apiPerMinute;
        }

        public void setApiPerMinute(int apiPerMinute) {
            this.apiPerMinute = apiPerMinute;
        }
    }
}
