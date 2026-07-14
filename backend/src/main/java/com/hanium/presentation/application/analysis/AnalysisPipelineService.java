package com.hanium.presentation.application.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.application.feedback.TextFeedbackResult;
import com.hanium.presentation.application.feedback.TextFeedbackService;
import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.common.util.AnalysisFailureMessages;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.analysis.type.AnalysisStep;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.infrastructure.client.analysis.AnalysisEngineClient;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineRequest;
import com.hanium.presentation.infrastructure.client.analysis.dto.AnalysisEngineResponse;
import com.hanium.presentation.infrastructure.client.videollm.VideoLlmEngineClient;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineRequest;
import com.hanium.presentation.infrastructure.client.videollm.dto.VideoLlmEngineResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@SuppressWarnings("null")
public class AnalysisPipelineService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisPipelineService.class);

    private final AnalysisJobRepository analysisJobRepository;
    private final FinalAnalysisResultRepository resultRepository;
    private final AnalysisEngineClient analysisEngineClient;
    private final VideoLlmEngineClient videoLlmEngineClient;
    private final TextFeedbackService textFeedbackService;
    private final ObjectMapper objectMapper;
    private final StorageCleanupService storageCleanupService;
    private final TransactionTemplate transactionTemplate;

    public AnalysisPipelineService(AnalysisJobRepository analysisJobRepository,
                                   FinalAnalysisResultRepository resultRepository,
                                   AnalysisEngineClient analysisEngineClient,
                                   VideoLlmEngineClient videoLlmEngineClient,
                                   TextFeedbackService textFeedbackService,
                                   ObjectMapper objectMapper,
                                   StorageCleanupService storageCleanupService,
                                   PlatformTransactionManager transactionManager) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
        this.analysisEngineClient = analysisEngineClient;
        this.videoLlmEngineClient = videoLlmEngineClient;
        this.textFeedbackService = textFeedbackService;
        this.objectMapper = objectMapper;
        this.storageCleanupService = storageCleanupService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("analysisTaskExecutor")
    public void runAnalysis(Long jobId) {
        try {
            if (stopIfCancellationRequested(jobId)) return;

            if (!update(jobId, AnalysisStep.PREPROCESSING, 10, "영상 분석을 준비하고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;

            AnalysisJob job = loadJobWithVideoAndUser(jobId);
            UploadedVideo video = job.getVideo();
            String filePath = video.getRelativePath();

            if (!update(jobId, AnalysisStep.AUDIO_ANALYSIS, 30, "음성, 자세, 시선, 표정 지표를 계산하고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            AnalysisEngineResponse basic = analysisEngineClient.analyze(
                    new AnalysisEngineRequest(job.getId(), video.getId(), filePath)
            );
            if (basic == null) {
                throw new IllegalStateException("analysis-engine returned empty response");
            }
            if (stopIfCancellationRequested(jobId)) return;

            if (!update(jobId, AnalysisStep.VIDEO_LLM_ANALYSIS, 75, "영상 장면을 AI가 관찰하고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            VideoLlmEngineResponse videoLlm = videoLlmEngineClient.analyze(
                    new VideoLlmEngineRequest(job.getId(), video.getId(), filePath)
            );
            if (videoLlm == null) {
                throw new IllegalStateException("video-llm-engine returned empty response");
            }
            if (stopIfCancellationRequested(jobId)) return;

            if (!update(jobId, AnalysisStep.RESULT_MERGING, 90, "분석 결과를 합치고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            TextFeedbackResult textFeedback = generateTextFeedbackSafely(jobId, basic, videoLlm);
            if (stopIfCancellationRequested(jobId)) return;
            if (!saveEngineResult(jobId, basic, videoLlm, textFeedback)) return;
            if (stopIfCancellationRequested(jobId)) return;
            complete(jobId);
        } catch (Exception exception) {
            if (stopIfCancellationRequested(jobId)) {
                log.info("Analysis cancelled before failure handling. jobId={}", jobId);
                return;
            }
            log.error("Analysis failed. jobId={}", jobId, exception);
            fail(jobId, AnalysisFailureMessages.safeMessageFrom(exception));
        }
    }

    AnalysisJob loadJobWithVideoAndUser(Long jobId) {
        return analysisJobRepository.findByIdWithVideoAndUser(jobId).orElseThrow();
    }

    boolean update(Long jobId, AnalysisStep step, int progress, String message) {
        return inTransaction(() -> {
            AnalysisJob job = analysisJobRepository.findByIdForUpdate(jobId).orElseThrow();
            if (applyCancellationIfNeeded(job)) {
                return false;
            }
            boolean updated = job.processing(step, progress, message);
            if (updated) {
                analysisJobRepository.save(job);
            }
            return updated;
        });
    }

    boolean saveEngineResult(Long jobId, AnalysisEngineResponse basic, VideoLlmEngineResponse videoLlm,
                             TextFeedbackResult textFeedback) throws JsonProcessingException {
        return inTransaction(() -> {
            AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUserForUpdate(jobId).orElseThrow();
            if (applyCancellationIfNeeded(job)) {
                return false;
            }
            if (resultRepository.findByJobId(jobId).isPresent()) {
                return true;
            }

            List<Map<String, Object>> scores = nullToEmptyList(basic.scores());
            List<Map<String, Object>> feedback = mergeFeedback(basic.observations(), videoLlm.observations());
            Map<String, Object> summary = mergeSummary(basic, videoLlm, textFeedback);
            double totalScore = basic.totalScore() == null ? 0.0 : basic.totalScore();

            try {
                resultRepository.save(new FinalAnalysisResult(job, totalScore,
                        objectMapper.writeValueAsString(scores),
                        objectMapper.writeValueAsString(feedback),
                        objectMapper.writeValueAsString(summary)));
                return true;
            } catch (JsonProcessingException exception) {
                throw new ResultJsonRuntimeException(exception);
            }
        });
    }

    boolean complete(Long jobId) {
        return inTransaction(() -> {
            AnalysisJob job = analysisJobRepository.findByIdForUpdate(jobId).orElseThrow();
            if (applyCancellationIfNeeded(job)) {
                return false;
            }
            boolean completed = job.done();
            if (completed) {
                analysisJobRepository.save(job);
            }
            return completed;
        });
    }

    void fail(Long jobId, String message) {
        inTransaction(() -> {
            analysisJobRepository.findByIdForUpdate(jobId).ifPresent(job -> {
                if (applyCancellationIfNeeded(job)) {
                    return;
                }
                if (job.fail(message)) {
                    analysisJobRepository.save(job);
                }
            });
            return null;
        });
    }

    boolean stopIfCancellationRequested(Long jobId) {
        return inTransaction(() -> analysisJobRepository.findByIdForUpdate(jobId)
                .map(this::applyCancellationIfNeeded)
                .orElse(true));
    }

    private boolean applyCancellationIfNeeded(AnalysisJob job) {
        if (job.getStatus() == AnalysisStatus.CANCEL_REQUESTED) {
            resultRepository.deleteByJobId(job.getId());
            job.cancel();
            analysisJobRepository.save(job);
            cleanupAnalysisArtifactsAfterCommit(job.getId());
            log.info("Analysis cancellation completed. jobId={} status={}", job.getId(), job.getStatus());
            return true;
        }
        if (job.getStatus() == AnalysisStatus.CANCELED) {
            log.info("Analysis already cancelled. jobId={} status={}", job.getId(), job.getStatus());
            return true;
        }
        return false;
    }

    private void cleanupAnalysisArtifactsAfterCommit(Long jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageCleanupService.cleanupAnalysisArtifacts(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageCleanupService.cleanupAnalysisArtifacts(jobId);
            }
        });
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }

    private TextFeedbackResult generateTextFeedbackSafely(Long jobId, AnalysisEngineResponse basic,
                                                          VideoLlmEngineResponse videoLlm) {
        try {
            TextFeedbackResult result = textFeedbackService.generate(jobId, basic, videoLlm);
            if (result != null) {
                return result;
            }
        } catch (Exception exception) {
            log.warn("Text LLM feedback service failed unexpectedly. jobId={} reason={}: {}",
                    jobId, exception.getClass().getSimpleName(), exception.getMessage());
        }
        return textFeedbackService.fallback(basic, "NONE", "", "text_feedback_service_unavailable");
    }

    private Map<String, Object> mergeSummary(AnalysisEngineResponse basic, VideoLlmEngineResponse videoLlm,
                                             TextFeedbackResult textFeedback) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (basic.summary() != null) {
            summary.putAll(basic.summary());
        }
        summary.put("durationSeconds", basic.durationSeconds());
        summary.put("transcript", basic.transcript() == null ? "" : basic.transcript());
        summary.put("stt", basic.stt());
        summary.put("metrics", basic.metrics());
        summary.put("videoInfo", basic.videoInfo());
        summary.put("videoLlmProvider", videoLlm.provider());
        summary.put("videoLlmMetadata", videoLlm.metadata());
        summary.put("sampledFrames", videoLlm.sampledFrames());
        summary.put("extractedAudio", videoLlm.audio());
        if (textFeedback != null) {
            summary.put("textFeedback", textFeedback.payload());
            summary.put("textLlm", textFeedback.metadata());
        }
        return summary;
    }

    private List<Map<String, Object>> mergeFeedback(List<Map<String, Object>> basicObservations,
                                                    List<Map<String, Object>> videoLlmObservations) {
        List<Map<String, Object>> feedback = new ArrayList<>();
        feedback.addAll(nullToEmptyList(basicObservations));
        feedback.addAll(nullToEmptyList(videoLlmObservations));
        for (int index = 0; index < feedback.size(); index++) {
            feedback.get(index).putIfAbsent("id", index + 1);
        }
        return feedback;
    }

    private List<Map<String, Object>> nullToEmptyList(List<Map<String, Object>> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private static final class ResultJsonRuntimeException extends RuntimeException {
        private ResultJsonRuntimeException(JsonProcessingException cause) {
            super(cause);
        }
    }
}
