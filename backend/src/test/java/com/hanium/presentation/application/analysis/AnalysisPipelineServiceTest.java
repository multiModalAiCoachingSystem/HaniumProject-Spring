package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.application.feedback.TextFeedbackResult;
import com.hanium.presentation.application.feedback.TextFeedbackService;
import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.infrastructure.client.analysis.AnalysisEngineClient;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import com.hanium.presentation.infrastructure.client.videollm.VideoLlmEngineClient;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class AnalysisPipelineServiceTest {
    private final AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
    private final FinalAnalysisResultRepository resultRepository = mock(FinalAnalysisResultRepository.class);
    private final AnalysisEngineClient analysisEngineClient = mock(AnalysisEngineClient.class);
    private final VideoLlmEngineClient videoLlmEngineClient = mock(VideoLlmEngineClient.class);
    private final TextFeedbackService textFeedbackService = mock(TextFeedbackService.class);
    private final VideoObservationLocalizationService videoObservationLocalizationService =
            new VideoObservationLocalizationService();
    private final StorageCleanupService storageCleanupService = mock(StorageCleanupService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisPipelineService pipelineService = new AnalysisPipelineService(
            analysisJobRepository,
            resultRepository,
            analysisEngineClient,
            videoLlmEngineClient,
            textFeedbackService,
            videoObservationLocalizationService,
            objectMapper,
            storageCleanupService,
            transactionManager()
    );

    @Test
    void runAnalysisCompletesQueuedJobAsDoneAndSavesFinalResult() throws Exception {
        AnalysisJob job = queuedJob();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUser(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(resultRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(analysisEngineClient.analyze(any())).thenReturn(basicResponse(job));
        when(videoLlmEngineClient.analyze(any())).thenReturn(videoLlmResponse(job));
        when(textFeedbackService.generate(any(), any(), any())).thenReturn(textFeedback());

        pipelineService.runAnalysis(job.getId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getProgressPercent()).isEqualTo(100);
        ArgumentCaptor<FinalAnalysisResult> resultCaptor = ArgumentCaptor.forClass(FinalAnalysisResult.class);
        verify(resultRepository).save(resultCaptor.capture());
        Map<String, Object> summary = objectMapper.readValue(resultCaptor.getValue().getSummaryJson(), Map.class);
        assertThat(summary).containsKeys("textFeedback", "textLlm");
        assertThat(summary).containsEntry("videoLlmProvider", "MOCK");
        assertThat(summary).containsEntry("videoLlmModel", "mock-model");
    }

    @Test
    void videoLlmEnglishObservationKeepsRawTextAndAddsKoreanDisplayFields() throws Exception {
        AnalysisJob job = queuedJob();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUser(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(resultRepository.findByJobId(job.getId())).thenReturn(Optional.empty());
        when(analysisEngineClient.analyze(any())).thenReturn(basicResponse(job));
        when(videoLlmEngineClient.analyze(any())).thenReturn(videoLlmResponseWithEnglishObservation(job));
        when(textFeedbackService.generate(any(), any(), any())).thenReturn(textFeedback());

        pipelineService.runAnalysis(job.getId());

        ArgumentCaptor<FinalAnalysisResult> resultCaptor = ArgumentCaptor.forClass(FinalAnalysisResult.class);
        verify(resultRepository).save(resultCaptor.capture());
        List<Map<String, Object>> feedback = objectMapper.readValue(resultCaptor.getValue().getFeedbackJson(),
                List.class);

        assertThat(feedback).hasSize(1);
        Map<String, Object> item = feedback.get(0);
        assertThat(item).containsEntry("rawTitle", "POSTURE visual strength");
        assertThat(item).containsEntry("rawDescription", "Upright stance with hands resting on podium surface");
        assertThat(item).containsEntry("titleKo", "자세 강점");
        assertThat(item.get("descriptionKo").toString()).contains("안정적인 자세");
        assertThat(item.get("recommendationKo").toString()).contains("안정적인 자세");
    }

    @Test
    void analysisEngineFailureMarksJobFailed() {
        AnalysisJob job = queuedJob();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUser(job.getId())).thenReturn(Optional.of(job));
        when(analysisEngineClient.analyze(any())).thenThrow(new RuntimeException("analysis-engine unavailable"));

        pipelineService.runAnalysis(job.getId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getErrorMessage()).doesNotContain("analysis-engine unavailable");
        verify(resultRepository, never()).save(any(FinalAnalysisResult.class));
    }

    @Test
    void videoLlmEngineFailureMarksJobFailedWithoutSavingFinalResult() {
        AnalysisJob job = queuedJob();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.findByIdWithVideoAndUser(job.getId())).thenReturn(Optional.of(job));
        when(analysisEngineClient.analyze(any())).thenReturn(basicResponse(job));
        when(videoLlmEngineClient.analyze(any())).thenThrow(new RuntimeException("video-llm-engine unavailable"));

        pipelineService.runAnalysis(job.getId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getErrorMessage()).doesNotContain("video-llm-engine unavailable");
        verify(resultRepository, never()).save(any(FinalAnalysisResult.class));
    }

    @Test
    void cancelRequestedJobIsNotCompletedAsDone() {
        AnalysisJob job = processingJob();
        job.requestCancel();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        boolean completed = pipelineService.complete(job.getId());

        assertThat(completed).isFalse();
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.CANCELED);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
    }

    @Test
    void cancelRequestedJobIsNotFailedAfterException() {
        AnalysisJob job = processingJob();
        job.requestCancel();
        when(analysisJobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        pipelineService.fail(job.getId(), "internal failure");

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.CANCELED);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
    }

    @Test
    void cancelRequestedJobDoesNotSaveFinalResult() throws Exception {
        AnalysisJob job = processingJob();
        job.requestCancel();
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        boolean saved = pipelineService.saveEngineResult(job.getId(), basicResponse(job), videoLlmResponse(job),
                textFeedback());

        assertThat(saved).isFalse();
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.CANCELED);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
        verify(resultRepository, never()).save(org.mockito.ArgumentMatchers.any(FinalAnalysisResult.class));
    }

    @Test
    void saveEngineResultReplacesExistingResultForSameJobBeforeSavingLatestValues() throws Exception {
        AnalysisJob job = queuedJob();
        FinalAnalysisResult existing = new FinalAnalysisResult(job, 11.0, "[]", "[]", "{}");
        ReflectionTestUtils.setField(existing, "id", 77L);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(resultRepository.findByJobId(job.getId())).thenReturn(Optional.of(existing));

        boolean saved = pipelineService.saveEngineResult(job.getId(), basicResponseWithScores(job),
                videoLlmResponse(job), textFeedback());

        assertThat(saved).isTrue();
        verify(resultRepository).deleteByJobId(job.getId());
        ArgumentCaptor<FinalAnalysisResult> resultCaptor = ArgumentCaptor.forClass(FinalAnalysisResult.class);
        verify(resultRepository).save(resultCaptor.capture());
        FinalAnalysisResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getTotalScore()).isEqualTo(77.5);
        Map<String, Object> summary = objectMapper.readValue(savedResult.getSummaryJson(), Map.class);
        assertThat(summary).containsEntry("totalScore", 77.5);
    }

    private AnalysisJob queuedJob() {
        User owner = User.createUser("owner@example.com", "encoded", "owner");
        owner.changeRole(UserRole.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UploadedVideo video = new UploadedVideo(owner, "video.mp4", "video.mp4", "uploads/video.mp4", "video/mp4", 1024L);
        ReflectionTestUtils.setField(video, "id", 10L);
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", 100L);
        return job;
    }

    private AnalysisJob processingJob() {
        User owner = User.createUser("owner@example.com", "encoded", "owner");
        owner.changeRole(UserRole.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UploadedVideo video = new UploadedVideo(owner, "video.mp4", "video.mp4", "uploads/video.mp4", "video/mp4", 1024L);
        ReflectionTestUtils.setField(video, "id", 10L);
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", 100L);
        job.processing(com.hanium.presentation.domain.analysis.type.AnalysisStep.PREPROCESSING, 10, "processing");
        return job;
    }

    private AnalysisEngineResponse basicResponse(AnalysisJob job) {
        return new AnalysisEngineResponse(job.getId(), job.getVideo().getId(), 10.0, Map.of(), "", Map.of(),
                Map.of(), List.of(), 0.0, Map.of(), List.of());
    }

    private AnalysisEngineResponse basicResponseWithScores(AnalysisJob job) {
        return new AnalysisEngineResponse(job.getId(), job.getVideo().getId(), 10.0, Map.of(), "", Map.of(),
                Map.of("eyeContactRatio", 0.82),
                List.of(Map.of("category", "EYE_CONTACT", "name", "시선", "score", 77.5, "weight", 0.20)),
                77.5,
                Map.of("overall", "요약"),
                List.of());
    }

    private VideoLlmEngineResponse videoLlmResponse(AnalysisJob job) {
        return new VideoLlmEngineResponse(job.getId(), job.getVideo().getId(), "MOCK", "mock-model",
                Map.of(), List.of(), Map.of(), List.of());
    }

    private VideoLlmEngineResponse videoLlmResponseWithEnglishObservation(AnalysisJob job) {
        return new VideoLlmEngineResponse(job.getId(), job.getVideo().getId(), "NVIDIA_NIM",
                "nvidia/nemotron-nano-12b-v2-vl", Map.of(), List.of(), Map.of(), List.of(Map.of(
                "type", "STRENGTH",
                "category", "POSTURE",
                "startSeconds", 12.0,
                "endSeconds", 22.0,
                "title", "POSTURE visual strength",
                "description", "Upright stance with hands resting on podium surface",
                "suggestion", "Keep this visual behavior consistent in future presentations."
        )));
    }

    private TextFeedbackResult textFeedback() {
        return TextFeedbackResult.success(Map.of(
                "presentationSummary", "요약",
                "overallFeedback", "종합 피드백",
                "strengths", List.of(),
                "weaknesses", List.of(),
                "improvementActions", List.of(),
                "expectedQuestions", List.of()
        ), "NONE", "");
    }

    private static AbstractPlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
