package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VideoObservationLocalizationServiceTest {
    private final VideoObservationLocalizationService service = new VideoObservationLocalizationService();

    @Test
    void strengthObservationUsesStrengthLevelAndTitle() {
        Map<String, Object> item = service.localizeOne(Map.of(
                "category", "GAZE",
                "level", "strength",
                "titleKo", "시선 개선 필요",
                "description", "Presenter keeps looking forward."
        ));

        assertThat(item).containsEntry("category", "EYE_CONTACT");
        assertThat(item).containsEntry("level", "STRENGTH");
        assertThat(item).containsEntry("type", "STRENGTH");
        assertThat(item).containsEntry("titleKo", "시선 강점");
        assertThat(item).containsEntry("rawTitle", "");
    }

    @Test
    void improvementObservationUsesImprovementLevelAndTitle() {
        Map<String, Object> item = service.localizeOne(Map.of(
                "category", "GESTURE",
                "level", "NEEDS_IMPROVEMENT",
                "titleKo", "제스처 강점",
                "suggestion", "Review this visual behavior."
        ));

        assertThat(item).containsEntry("category", "GESTURE");
        assertThat(item).containsEntry("level", "IMPROVEMENT");
        assertThat(item).containsEntry("type", "WEAKNESS");
        assertThat(item).containsEntry("titleKo", "제스처 개선 필요");
        assertThat(item.get("recommendationKo").toString()).contains("손동작");
    }

    @Test
    void neutralObservationIsNotForcedIntoStrengthOrImprovement() {
        Map<String, Object> item = service.localizeOne(Map.of(
                "category", "POSTURE"
        ));

        assertThat(item).containsEntry("level", "NEUTRAL");
        assertThat(item).containsEntry("type", "INFO");
        assertThat(item).containsEntry("titleKo", "자세 참고");
    }

    @Test
    void weaknessTypeIsNormalizedToImprovementLevel() {
        Map<String, Object> item = service.localizeOne(Map.of(
                "category", "EXPRESSION",
                "type", "WEAKNESS"
        ));

        assertThat(item).containsEntry("level", "IMPROVEMENT");
        assertThat(item).containsEntry("type", "WEAKNESS");
        assertThat(item).containsEntry("titleKo", "표정 개선 필요");
    }
}
