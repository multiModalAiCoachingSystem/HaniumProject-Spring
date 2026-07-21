package com.hanium.presentation.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class ApiDateTimes {
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private ApiDateTimes() {
    }

    public static OffsetDateTime inServiceZone(LocalDateTime value) {
        return value == null ? null : value.atZone(SERVICE_ZONE).toOffsetDateTime();
    }
}
