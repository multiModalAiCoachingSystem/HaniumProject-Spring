package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.analysis.type.AnalysisStep;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class AnalysisRecoveryServiceTest {
    private final AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
    private final FinalAnalysisResultRepository resultRepository = mock(FinalAnalysisResultRepository.class);
    private final AnalysisPipelineService pipelineService = mock(AnalysisPipelineService.class);
    private final StorageCleanupService storageCleanupService = mock(StorageCleanupService.class);

    @TempDir
    Path storageRoot;

    @Test
    void queuedJobIsClaimedAndRecoveredOnce() throws IOException {
        AnalysisJob job = job(AnalysisStatus.QUEUED);
        createSourceVideo(job);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.claimQueuedForRecovery(job.getId(), 0, 3, "STARTUP_QUEUED"))
                .thenReturn(1);

        AnalysisRecoveryService.RecoverySummary summary = service().recoverOnStartup();

        assertThat(summary.queuedCount()).isEqualTo(1);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
        verify(pipelineService).runRecoveredAnalysis(job.getId());
    }

    @Test
    void processingJobIsRequeuedAndStartedAgain() throws IOException {
        AnalysisJob job = job(AnalysisStatus.PROCESSING);
        createSourceVideo(job);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.requeueProcessingForRecovery(job.getId(), 0, 3, "STARTUP_PROCESSING"))
                .thenReturn(1);

        service().recoverOnStartup();

        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
        verify(pipelineService).runAnalysis(job.getId());
    }

    @Test
    void cancelRequestedJobIsFinalizedWithoutRestartingAnalysis() {
        AnalysisJob job = job(AnalysisStatus.CANCEL_REQUESTED);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.finalizeCancelRequestedForRecovery(
                job.getId(), "STARTUP_CANCEL_REQUESTED")).thenReturn(1);

        service().recoverOnStartup();

        verify(resultRepository).deleteByJobId(job.getId());
        verify(storageCleanupService).cleanupAnalysisArtifacts(job.getId());
        verify(pipelineService, never()).runAnalysis(job.getId());
        verify(pipelineService, never()).runRecoveredAnalysis(job.getId());
    }

    @Test
    void terminalStatusesAreNeverRecoveredEvenIfRepositoryReturnsThem() {
        List<AnalysisJob> terminalJobs = List.of(
                job(AnalysisStatus.DONE),
                job(AnalysisStatus.FAILED),
                job(AnalysisStatus.CANCELED)
        );
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(terminalJobs);

        service().recoverOnStartup();

        verify(pipelineService, never()).runAnalysis(org.mockito.ArgumentMatchers.any());
        verify(pipelineService, never()).runRecoveredAnalysis(org.mockito.ArgumentMatchers.any());
        verify(resultRepository, never()).deleteByJobId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingSourceVideoFailsWithSafeReason() {
        AnalysisJob job = job(AnalysisStatus.QUEUED);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.failRecoverableJob(
                job.getId(),
                "QUEUED",
                AnalysisRecoveryService.SAFE_VIDEO_MISSING_MESSAGE,
                AnalysisRecoveryService.VIDEO_FILE_NOT_FOUND
        )).thenReturn(1);

        service().recoverOnStartup();

        verify(analysisJobRepository).failRecoverableJob(
                job.getId(),
                "QUEUED",
                "원본 영상 파일을 찾을 수 없어 분석을 다시 시작하지 못했습니다.",
                "VIDEO_FILE_NOT_FOUND"
        );
        verify(pipelineService, never()).runAnalysis(job.getId());
        verify(pipelineService, never()).runRecoveredAnalysis(job.getId());
    }

    @Test
    void recoveryAttemptLimitFailsJobWithoutRequeue() throws IOException {
        AnalysisJob job = job(AnalysisStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "recoveryAttemptCount", 3);
        createSourceVideo(job);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.failRecoverableJob(
                job.getId(),
                "PROCESSING",
                AnalysisRecoveryService.SAFE_RECOVERY_LIMIT_MESSAGE,
                AnalysisRecoveryService.RECOVERY_LIMIT_EXCEEDED
        )).thenReturn(1);

        service().recoverOnStartup();

        verify(analysisJobRepository, never()).requeueProcessingForRecovery(
                job.getId(), 3, 3, "STARTUP_PROCESSING");
        verify(pipelineService, never()).runAnalysis(job.getId());
    }

    @Test
    void runningRecoveryTwiceDoesNotScheduleSameQueuedJobTwice() throws IOException {
        AnalysisJob job = job(AnalysisStatus.QUEUED);
        createSourceVideo(job);
        when(analysisJobRepository.findByStatusInOrderByCreatedAtAsc(recoverableStatuses()))
                .thenReturn(List.of(job));
        when(analysisJobRepository.claimQueuedForRecovery(job.getId(), 0, 3, "STARTUP_QUEUED"))
                .thenReturn(1, 0);

        AnalysisRecoveryService recoveryService = service();
        recoveryService.recoverOnStartup();
        recoveryService.recoverOnStartup();

        verify(pipelineService, times(1)).runRecoveredAnalysis(job.getId());
    }

    private AnalysisRecoveryService service() {
        return new AnalysisRecoveryService(
                analysisJobRepository,
                resultRepository,
                pipelineService,
                storageCleanupService,
                transactionManager(),
                storageRoot.toString(),
                3
        );
    }

    private List<AnalysisStatus> recoverableStatuses() {
        return List.of(AnalysisStatus.QUEUED, AnalysisStatus.PROCESSING, AnalysisStatus.CANCEL_REQUESTED);
    }

    private AnalysisJob job(AnalysisStatus status) {
        User owner = User.createUser("owner@example.com", "encoded", "owner");
        owner.changeRole(UserRole.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);
        UploadedVideo video = new UploadedVideo(
                owner,
                "video.mp4",
                "video.mp4",
                "uploads/recovery-video.mp4",
                "video/mp4",
                1024L
        );
        ReflectionTestUtils.setField(video, "id", 10L);
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", 100L + status.ordinal());
        if (status == AnalysisStatus.PROCESSING) {
            job.processing(AnalysisStep.PREPROCESSING, 10, "processing");
        } else if (status == AnalysisStatus.CANCEL_REQUESTED) {
            job.processing(AnalysisStep.PREPROCESSING, 10, "processing");
            job.requestCancel();
        } else if (status == AnalysisStatus.DONE) {
            job.done();
        } else if (status == AnalysisStatus.FAILED) {
            job.fail("failed");
        } else if (status == AnalysisStatus.CANCELED) {
            job.cancel();
        }
        return job;
    }

    private void createSourceVideo(AnalysisJob job) throws IOException {
        Path source = storageRoot.resolve(job.getVideo().getRelativePath());
        Files.createDirectories(source.getParent());
        Files.writeString(source, "video");
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
