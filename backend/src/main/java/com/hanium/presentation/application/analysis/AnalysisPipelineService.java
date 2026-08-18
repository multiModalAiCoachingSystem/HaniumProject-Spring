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
    private final VideoObservationLocalizationService videoObservationLocalizationService;
    private final ObjectMapper objectMapper;
    private final StorageCleanupService storageCleanupService;
    private final TransactionTemplate transactionTemplate;

    public AnalysisPipelineService(AnalysisJobRepository analysisJobRepository,
                                   FinalAnalysisResultRepository resultRepository,
                                   AnalysisEngineClient analysisEngineClient,
                                   VideoLlmEngineClient videoLlmEngineClient,
                                   TextFeedbackService textFeedbackService,
                                   VideoObservationLocalizationService videoObservationLocalizationService,
                                   ObjectMapper objectMapper,
                                   StorageCleanupService storageCleanupService,
                                   PlatformTransactionManager transactionManager) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
        this.analysisEngineClient = analysisEngineClient;
        this.videoLlmEngineClient = videoLlmEngineClient;
        this.textFeedbackService = textFeedbackService;
        this.videoObservationLocalizationService = videoObservationLocalizationService;
        this.objectMapper = objectMapper;
        this.storageCleanupService = storageCleanupService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("analysisTaskExecutor")
    public void runAnalysis(Long jobId) {
        try {
            if (!claimQueuedForProcessing(jobId)) {
                log.info("[JOB] 실행 가능한 대기 작업이 아니어서 분석을 건너뜁니다. jobId={}", jobId);
                return;
            }
            log.info("[JOB] 분석을 시작합니다. jobId={} status=PROCESSING", jobId);
        } catch (RuntimeException exception) {
            log.warn("[JOB] 분석 작업 상태 전환에 실패했습니다. jobId={} reason=DATABASE_CLAIM_ERROR", jobId);
            return;
        }
        executeAnalysis(jobId);
    }

    @Async("analysisTaskExecutor")
    public void runRecoveredAnalysis(Long jobId) {
        try {
            if (!isRecoveredJobRunnable(jobId)) {
                log.info("[RECOVERY] 상태가 변경되어 복구 분석을 건너뜁니다. jobId={}", jobId);
                return;
            }
            log.info("[RECOVERY] 복구된 분석을 시작합니다. jobId={}", jobId);
        } catch (RuntimeException exception) {
            log.warn("[RECOVERY] 복구 작업 검증에 실패했습니다. jobId={} reason=DATABASE_CLAIM_ERROR", jobId);
            return;
        }
        executeAnalysis(jobId);
    }

    private void executeAnalysis(Long jobId) {
        long pipelineStarted = System.nanoTime();
        double analysisEngineSeconds = 0.0;
        double videoLlmSeconds = 0.0;
        double textFeedbackSeconds = 0.0;
        double resultSaveSeconds = 0.0;
        log.info("[ANALYSIS] 전체 분석 파이프라인을 시작합니다. jobId={}", jobId);
        try {
            if (stopIfCancellationRequested(jobId)) return;

            AnalysisJob job = loadJobWithVideoAndUser(jobId);
            UploadedVideo video = job.getVideo();
            String filePath = video.getRelativePath();

            if (!update(jobId, AnalysisStep.AUDIO_ANALYSIS, 30, "음성, 자세, 시선, 표정 지표를 계산하고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            long analysisEngineStarted = System.nanoTime();
            AnalysisEngineResponse basic = analysisEngineClient.analyze(
                    new AnalysisEngineRequest(job.getId(), video.getId(), filePath)
            );
            analysisEngineSeconds = elapsedSeconds(analysisEngineStarted);
            if (basic == null) {
                throw new IllegalStateException("analysis-engine returned empty response");
            }
            if (stopIfCancellationRequested(jobId)) return;

            if (!update(jobId, AnalysisStep.VIDEO_LLM_ANALYSIS, 75, "영상 장면을 AI가 관찰하고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            log.info("[VIDEO_LLM] 비디오 LLM 분석을 시작합니다. jobId={}", job.getId());
            long videoLlmStarted = System.nanoTime();
            VideoLlmEngineResponse videoLlm = videoLlmEngineClient.analyze(
                    new VideoLlmEngineRequest(job.getId(), video.getId(), filePath)
            );
            videoLlmSeconds = elapsedSeconds(videoLlmStarted);
            if (videoLlm == null) {
                throw new IllegalStateException("video-llm-engine returned empty response");
            }
            log.info("[VIDEO_LLM] 비디오 LLM 분석이 끝났습니다. jobId={} provider={} model={} "
                            + "fallbackUsed={} elapsed={}s",
                    job.getId(), videoLlm.provider(), videoLlm.model(), videoLlmFallbackUsed(videoLlm),
                    roundSeconds(videoLlmSeconds));
            if (stopIfCancellationRequested(jobId)) return;

            if (!update(jobId, AnalysisStep.RESULT_MERGING, 90, "분석 결과를 합치고 있습니다.")) return;
            if (stopIfCancellationRequested(jobId)) return;
            log.info("[FEEDBACK] 발표 피드백 생성을 시작합니다. jobId={}", jobId);
            long textFeedbackStarted = System.nanoTime();
            TextFeedbackResult textFeedback = generateTextFeedbackSafely(jobId, basic, videoLlm);
            textFeedbackSeconds = elapsedSeconds(textFeedbackStarted);
            log.info("[FEEDBACK] 발표 피드백 생성이 끝났습니다. jobId={} provider={} model={} "
                            + "fallbackUsed={} elapsed={}s",
                    jobId, textFeedback.provider(), textFeedback.model(), textFeedback.fallback(),
                    roundSeconds(textFeedbackSeconds));
            if (stopIfCancellationRequested(jobId)) return;
            log.info("[RESULT] 분석 결과 저장을 시작합니다. jobId={}", jobId);
            long resultSaveStarted = System.nanoTime();
            if (!saveEngineResult(jobId, basic, videoLlm, textFeedback)) return;
            resultSaveSeconds = elapsedSeconds(resultSaveStarted);
            log.info("[RESULT] 분석 결과 저장이 끝났습니다. jobId={} elapsed={}s",
                    jobId, roundSeconds(resultSaveSeconds));
            if (stopIfCancellationRequested(jobId)) return;
            complete(jobId);
        } catch (Exception exception) {
            if (stopIfCancellationRequested(jobId)) {
                log.info("[ANALYSIS] 실패 처리 전에 취소 상태를 확인했습니다. jobId={}", jobId);
                return;
            }
            String safeReason = AnalysisFailureMessages.safeMessageFrom(exception);
            log.error("[ANALYSIS] 분석 중 오류가 발생했습니다. jobId={} reason={}", jobId, safeReason);
            fail(jobId, safeReason);
        } finally {
            log.info(
                    "[ANALYSIS] 전체 분석 파이프라인이 끝났습니다. jobId={} analysisEngineSeconds={} "
                            + "videoLlmSeconds={} textFeedbackSeconds={} resultSaveSeconds={} totalSeconds={}",
                    jobId,
                    roundSeconds(analysisEngineSeconds),
                    roundSeconds(videoLlmSeconds),
                    roundSeconds(textFeedbackSeconds),
                    roundSeconds(resultSaveSeconds),
                    roundSeconds(elapsedSeconds(pipelineStarted))
            );
        }
    }

    private static double elapsedSeconds(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    }

    private static double roundSeconds(double seconds) {
        return Math.round(seconds * 1000.0) / 1000.0;
    }

    boolean claimQueuedForProcessing(Long jobId) {
        return inTransaction(() -> analysisJobRepository.claimQueuedForProcessing(jobId) == 1);
    }

    boolean isRecoveredJobRunnable(Long jobId) {
        return inTransaction(() -> analysisJobRepository.findByIdForUpdate(jobId)
                .map(job -> job.getStatus() == AnalysisStatus.PROCESSING
                        || job.getStatus() == AnalysisStatus.CANCEL_REQUESTED)
                .orElse(false));
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
            if (job.getStatus() != AnalysisStatus.PROCESSING) {
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
            if (job.getStatus() != AnalysisStatus.PROCESSING) {
                return false;
            }

            List<Map<String, Object>> scores = nullToEmptyList(basic.scores());
            List<Map<String, Object>> feedback = mergeFeedback(basic.observations(), videoLlm.observations());
            Map<String, Object> summary = mergeSummary(basic, videoLlm, textFeedback);
            double totalScore = basic.totalScore() == null ? 0.0 : basic.totalScore();

            try {
                resultRepository.findByJobId(jobId).ifPresent(existing -> {
                    log.warn("[RESULT] 기존 결과를 최신 결과로 교체합니다. jobId={} resultId={}",
                            jobId, existing.getId());
                    resultRepository.deleteByJobId(jobId);
                });
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
            if (job.getStatus() != AnalysisStatus.PROCESSING) {
                return false;
            }
            boolean completed = job.done();
            if (completed) {
                analysisJobRepository.save(job);
                log.info("[JOB] 분석이 완료되었습니다. jobId={} status={}", job.getId(), job.getStatus());
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
                if (job.getStatus() != AnalysisStatus.PROCESSING) {
                    return;
                }
                if (job.fail(message)) {
                    analysisJobRepository.save(job);
                    log.error("[JOB] 분석이 실패했습니다. jobId={} status={} reason={}",
                            job.getId(), job.getStatus(), message);
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
            log.info("[JOB] 분석이 취소되었습니다. jobId={} status={}", job.getId(), job.getStatus());
            return true;
        }
        if (job.getStatus() == AnalysisStatus.CANCELED) {
            log.debug("[JOB] 이미 취소된 작업입니다. jobId={} status={}", job.getId(), job.getStatus());
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
            log.warn("[FEEDBACK] 피드백 서비스 오류로 fallback을 사용합니다. jobId={} reason={}",
                    jobId, exception.getClass().getSimpleName());
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
        summary.put("totalScore", basic.totalScore() == null ? 0.0 : basic.totalScore());
        summary.put("transcript", basic.transcript() == null ? "" : basic.transcript());
        summary.put("stt", basic.stt());
        summary.put("metrics", basic.metrics());
        summary.put("videoInfo", basic.videoInfo());
        summary.put("videoLlmProvider", videoLlm.provider());
        summary.put("videoLlmModel", videoLlm.model());
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
        return videoObservationLocalizationService.localize(feedback);
    }

    private boolean videoLlmFallbackUsed(VideoLlmEngineResponse videoLlm) {
        if (videoLlm.metadata() == null) {
            return false;
        }
        Object fallbackUsed = videoLlm.metadata().get("fallbackUsed");
        return Boolean.TRUE.equals(fallbackUsed) || "true".equalsIgnoreCase(String.valueOf(fallbackUsed));
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
