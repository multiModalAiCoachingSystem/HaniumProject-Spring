package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void duplicateObservationsWithinTwoSecondsKeepHighestConfidenceOnly() {
        List<Map<String, Object>> localized = service.localize(List.of(
                Map.of("category", "POSTURE", "level", "STRENGTH", "startSeconds", 8.0,
                        "description", "Stable posture.", "confidence", 0.71),
                Map.of("category", "POSTURE", "level", "STRENGTH", "startSeconds", 8.4,
                        "description", "Stable posture.", "confidence", 0.93),
                Map.of("category", "POSTURE", "level", "STRENGTH", "startSeconds", 8.6,
                        "description", "Stable posture.", "confidence", 0.82)
        ));

        assertThat(localized).hasSize(1);
        assertThat(localized.get(0)).containsEntry("confidence", 0.93);
        assertThat(localized.get(0)).containsEntry("id", 1);
        assertThat(localized.get(0)).containsEntry("startSeconds", 8.4);
    }

    @Test
    void strengthWithImprovementDescriptionUsesStrengthDisplayCopy() {
        Map<String, Object> item = service.localizeOne(Map.of(
                "category", "EYE_CONTACT",
                "level", "STRENGTH",
                "description", "청중 또는 카메라를 바라보는 시간이 부족합니다.",
                "recommendationKo", "시선 이탈을 줄일 필요가 있습니다."
        ));

        assertThat(item.get("descriptionKo").toString()).doesNotContain("부족");
        assertThat(item.get("recommendationKo").toString()).contains("유지");
    }

    @Test
    void onlyQualityWarningNeutralIsMarkedForDisplay() {
        Map<String, Object> ordinary = service.localizeOne(Map.of(
                "category", "POSTURE",
                "level", "NEUTRAL",
                "description", "General observation"
        ));
        Map<String, Object> qualityWarning = service.localizeOne(Map.of(
                "category", "VIDEO_QUALITY",
                "level", "NEUTRAL",
                "description", "The face is out of frame and confidence is low."
        ));

        assertThat(ordinary).containsEntry("displayNeutral", false);
        assertThat(qualityWarning).containsEntry("displayNeutral", true);
    }
}
