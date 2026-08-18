package com.hanium.presentation.global.properties;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.video")
public class VideoUploadProperties {
    private double minDurationSeconds = 5.0;
    private double maxDurationSeconds = 600.0;

    @PostConstruct
    void validate() {
        if (!Double.isFinite(minDurationSeconds) || !Double.isFinite(maxDurationSeconds)
                || minDurationSeconds <= 0 || maxDurationSeconds <= minDurationSeconds) {
            throw new IllegalStateException(
                    "VIDEO_MIN_DURATION_SECONDS and VIDEO_MAX_DURATION_SECONDS must define a positive range"
            );
        }
    }

    public double getMinDurationSeconds() {
        return minDurationSeconds;
    }

    public void setMinDurationSeconds(double minDurationSeconds) {
        this.minDurationSeconds = minDurationSeconds;
    }

    public double getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(double maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }
}
