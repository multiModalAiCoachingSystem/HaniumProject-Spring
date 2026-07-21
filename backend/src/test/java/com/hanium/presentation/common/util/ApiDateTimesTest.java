package com.hanium.presentation.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ApiDateTimesTest {

    @Test
    void convertsKoreanWallClockTimeToOffsetDateTimeWithoutChangingClockFields() {
        OffsetDateTime value = ApiDateTimes.inServiceZone(LocalDateTime.of(2026, 7, 21, 16, 28));

        assertThat(value.toString()).isEqualTo("2026-07-21T16:28+09:00");
    }

    @Test
    void serializesApiTimeWithExplicitOffsetAcrossMidnight() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OffsetDateTime value = ApiDateTimes.inServiceZone(LocalDateTime.of(2026, 7, 22, 0, 5));

        assertThat(objectMapper.writeValueAsString(value)).isEqualTo("\"2026-07-22T00:05:00+09:00\"");
    }

    @Test
    void preservesNullForOptionalCompletionTime() {
        assertThat(ApiDateTimes.inServiceZone(null)).isNull();
    }
}
