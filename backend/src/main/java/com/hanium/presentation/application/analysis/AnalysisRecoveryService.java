package com.hanium.presentation.application.analysis;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisRecoveryService {
    static final String VIDEO_FILE_NOT_FOUND = "VIDEO_FILE_NOT_FOUND";
    static final String RECOVERY_LIMIT_EXCEEDED = "RECOVERY_LIMIT_EXCEEDED";
    static final String SAFE_VIDEO_MISSING_MESSAGE = "원본 영상 파일을 찾을 수 없어 분석을 다시 시작하지 못했습니다.";
    static final String SAFE_RECOVERY_LIMIT_MESSAGE = "자동 복구 횟수를 초과했습니다. 다시 분석을 시도해 주세요.";

    private static final String STARTUP_QUEUED = "STARTUP_QUEUED";
    private static final String STARTUP_PROCESSING = "STARTUP_PROCESSING";
    private static final String STARTUP_CANCEL_REQUESTED = "STARTUP_CANCEL_REQUESTED";
    private static final Logger log = LoggerFactory.getLogger(AnalysisRecoveryService.class);

    private final AnalysisJobRepository analysisJobRepository;
    private final FinalAnalysisResultRepository resultRepository;
    private final AnalysisPipelineService pipelineService;
    private final StorageCleanupService storageCleanupService;
    private final TransactionTemplate transactionTemplate;
    private final Path storageRoot;
    private final int maxRecoveryAttempts;

    public AnalysisRecoveryService(AnalysisJobRepository analysisJobRepository,
                                   FinalAnalysisResultRepository resultRepository,
                                   AnalysisPipelineService pipelineService,
                                   StorageCleanupService storageCleanupService,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${app.storage.root:${STORAGE_ROOT:/app/storage}}") String storageRoot,
                                   @Value("${app.analysis-recovery.max-attempts:3}") int maxRecoveryAttempts) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
        this.pipelineService = pipelineService;
        this.storageCleanupService = storageCleanupService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxRecoveryAttempts = Math.max(1, maxRecoveryAttempts);
    }

    public RecoverySummary recoverOnStartup() {
        List<AnalysisJob> candidates = analysisJobRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING, AnalysisStatus.CANCEL_REQUESTED)
        );
        Map<AnalysisStatus, Long> counts = new EnumMap<>(AnalysisStatus.class);
        for (AnalysisJob candidate : candidates) {
            counts.merge(candidate.getStatus(), 1L, Long::sum);
        }
        RecoverySummary summary = new RecoverySummary(
                counts.getOrDefault(AnalysisStatus.QUEUED, 0L),
                counts.getOrDefault(AnalysisStatus.PROCESSING, 0L),
                counts.getOrDefault(AnalysisStatus.CANCEL_REQUESTED, 0L)
        );
        log.info("Backend recovery started: queuedCount={} processingCount={} cancelRequestedCount={}",
                summary.queuedCount(), summary.processingCount(), summary.cancelRequestedCount());

        for (AnalysisJob candidate : candidates) {
            try {
                recoverCandidate(candidate);
            } catch (RuntimeException exception) {
                log.warn("Job recovery failed: jobId={} previousStatus={} reason=RECOVERY_INTERNAL_ERROR",
                        candidate.getId(), candidate.getStatus());
            }
        }
        return summary;
    }

    private void recoverCandidate(AnalysisJob candidate) {
        AnalysisStatus previousStatus = candidate.getStatus();
        if (previousStatus == AnalysisStatus.CANCEL_REQUESTED) {
            finalizeCancellation(candidate.getId(), previousStatus);
            return;
        }
        if (previousStatus != AnalysisStatus.QUEUED && previousStatus != AnalysisStatus.PROCESSING) {
            return;
        }
        if (!sourceVideoExists(candidate)) {
            failRecovery(candidate.getId(), previousStatus, SAFE_VIDEO_MISSING_MESSAGE, VIDEO_FILE_NOT_FOUND);
            return;
        }
        if (candidate.getRecoveryAttemptCount() >= maxRecoveryAttempts) {
            failRecovery(candidate.getId(), previousStatus, SAFE_RECOVERY_LIMIT_MESSAGE, RECOVERY_LIMIT_EXCEEDED);
            return;
        }
        if (previousStatus == AnalysisStatus.QUEUED) {
            recoverQueued(candidate);
        } else {
            recoverProcessing(candidate);
        }
    }

    private void recoverQueued(AnalysisJob candidate) {
        Boolean recovered = transactionTemplate.execute(status -> {
            int claimed = analysisJobRepository.claimQueuedForRecovery(
                    candidate.getId(),
                    candidate.getRecoveryAttemptCount(),
                    maxRecoveryAttempts,
                    STARTUP_QUEUED
            );
            if (claimed != 1) {
                return false;
            }
            resultRepository.deleteByJobId(candidate.getId());
            afterCommit(() -> {
                storageCleanupService.cleanupAnalysisArtifacts(candidate.getId());
                pipelineService.runRecoveredAnalysis(candidate.getId());
            });
            return true;
        });
        if (Boolean.TRUE.equals(recovered)) {
            log.info("Queued job recovered: jobId={} previousStatus=QUEUED newStatus=PROCESSING action=REENQUEUED",
                    candidate.getId());
        }
    }

    private void recoverProcessing(AnalysisJob candidate) {
        Boolean recovered = transactionTemplate.execute(status -> {
            int requeued = analysisJobRepository.requeueProcessingForRecovery(
                    candidate.getId(),
                    candidate.getRecoveryAttemptCount(),
                    maxRecoveryAttempts,
                    STARTUP_PROCESSING
            );
            if (requeued != 1) {
                return false;
            }
            resultRepository.deleteByJobId(candidate.getId());
            afterCommit(() -> {
                storageCleanupService.cleanupAnalysisArtifacts(candidate.getId());
                pipelineService.runAnalysis(candidate.getId());
            });
            return true;
        });
        if (Boolean.TRUE.equals(recovered)) {
            log.info("Processing job recovered: jobId={} previousStatus=PROCESSING newStatus=QUEUED action=REQUEUED",
                    candidate.getId());
        }
    }

    private void finalizeCancellation(Long jobId, AnalysisStatus previousStatus) {
        Boolean recovered = transactionTemplate.execute(status -> {
            int finalized = analysisJobRepository.finalizeCancelRequestedForRecovery(
                    jobId,
                    STARTUP_CANCEL_REQUESTED
            );
            if (finalized != 1) {
                return false;
            }
            resultRepository.deleteByJobId(jobId);
            afterCommit(() -> storageCleanupService.cleanupAnalysisArtifacts(jobId));
            return true;
        });
        if (Boolean.TRUE.equals(recovered)) {
            log.info("Cancel requested job recovered: jobId={} previousStatus={} newStatus=CANCELED action=CANCEL_FINALIZED",
                    jobId, previousStatus);
        }
    }

    private void failRecovery(Long jobId, AnalysisStatus previousStatus, String safeMessage, String reason) {
        Boolean failed = transactionTemplate.execute(status -> {
            int updated = analysisJobRepository.failRecoverableJob(
                    jobId,
                    previousStatus.name(),
                    safeMessage,
                    reason
            );
            if (updated != 1) {
                return false;
            }
            resultRepository.deleteByJobId(jobId);
            afterCommit(() -> storageCleanupService.cleanupAnalysisArtifacts(jobId));
            return true;
        });
        if (Boolean.TRUE.equals(failed)) {
            log.warn("Job recovery failed: jobId={} previousStatus={} reason={}", jobId, previousStatus, reason);
        }
    }

    private boolean sourceVideoExists(AnalysisJob job) {
        String relativePath = job.getVideo().getRelativePath();
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        Path source = storageRoot.resolve(relativePath).normalize();
        return source.startsWith(storageRoot) && Files.isRegularFile(source);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record RecoverySummary(long queuedCount, long processingCount, long cancelRequestedCount) {
    }
}
