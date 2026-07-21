package com.hanium.presentation.application.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.application.analysis.VideoObservationLocalizationService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.presentation.dto.response.ResultDetailResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResultQueryServiceTest {
    private final FinalAnalysisResultRepository resultRepository = mock(FinalAnalysisResultRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResultQueryService resultQueryService = new ResultQueryService(
            resultRepository, objectMapper, new VideoObservationLocalizationService());

    @Test
    void detailReturnsMetricsVideoLlmMetadataAndObservationsFromStoredJson() throws Exception {
        User owner = User.createUser("owner@example.com", "encoded", "owner");
        owner.changeRole(UserRole.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UploadedVideo video = new UploadedVideo(owner, "video.mp4", "video.mp4", "uploads/video.mp4", "video/mp4", 1024L);
        ReflectionTestUtils.setField(video, "id", 10L);
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", 100L);

        List<Map<String, Object>> scores = List.of(
                Map.of("category", "POSTURE", "name", "자세", "score", 80.0, "weight", 0.25),
                Map.of("category", "EXPRESSION", "name", "표정", "score", 70.0, "weight", 0.20),
                Map.of("category", "EYE_CONTACT", "name", "시선", "score", 90.0, "weight", 0.20),
                Map.of("category", "VOICE", "name", "음성", "score", 75.0, "weight", 0.25),
                Map.of("category", "GESTURE", "name", "제스처", "score", 60.0, "weight", 0.10)
        );
        List<Map<String, Object>> feedback = List.of(Map.of(
                "id", 1,
                "category", "GAZE",
                "level", "STRENGTH",
                "titleKo", "시선 처리 강점"
        ));
        Map<String, Object> metrics = Map.of(
                "eyeContactRatio", 0.82,
                "gestureActivityScore", 61.0,
                "pauseRatio", 0.12
        );
        Map<String, Object> videoLlmMetadata = Map.of(
                "requestedProvider", "NVIDIA_NIM",
                "actualProvider", "NVIDIA_NIM",
                "actualModel", "nvidia/nemotron-nano-12b-v2-vl",
                "fallbackUsed", false
        );
        Map<String, Object> summary = Map.of(
                "overall", "요약",
                "totalScore", 76.8,
                "metrics", metrics,
                "videoLlmMetadata", videoLlmMetadata
        );

        FinalAnalysisResult result = new FinalAnalysisResult(job, 76.8,
                objectMapper.writeValueAsString(scores),
                objectMapper.writeValueAsString(feedback),
                objectMapper.writeValueAsString(summary));
        ReflectionTestUtils.setField(result, "id", 200L);
        when(resultRepository.findById(200L)).thenReturn(Optional.of(result));

        ResultDetailResponse response = resultQueryService.detailByResultId(owner, 200L);

        assertThat(response.totalScore()).isEqualTo(76.8);
        assertThat(response.scores()).hasSize(5);
        assertThat(response.metrics()).containsEntry("eyeContactRatio", 0.82);
        assertThat(response.videoLlmMetadata()).containsEntry("actualProvider", "NVIDIA_NIM");
        assertThat(response.observations()).hasSize(1);
        assertThat(response.summary()).containsEntry("totalScore", 76.8);
    }
}
