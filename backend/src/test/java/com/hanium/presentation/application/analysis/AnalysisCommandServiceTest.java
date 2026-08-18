package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.application.video.VideoFileQueryService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.analysis.type.AnalysisStep;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.AnalysisAlreadyActiveException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class AnalysisCommandServiceTest {
    private final AnalysisJobRepository analysisJobRepository = Mockito.mock(AnalysisJobRepository.class);
    private final FinalAnalysisResultRepository resultRepository = Mockito.mock(FinalAnalysisResultRepository.class);
    private final UploadedVideoRepository uploadedVideoRepository = Mockito.mock(UploadedVideoRepository.class);
    private final VideoFileQueryService videoFileQueryService = Mockito.mock(VideoFileQueryService.class);
    private final AnalysisPipelineService pipelineService = Mockito.mock(AnalysisPipelineService.class);

    @TempDir
    Path storageRoot;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void startCreatesQueuedJobForOwnedVideoAndRunsPipelineAfterCommit() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        when(videoFileQueryService.loadOwnedVideoForUpdate(owner, video.getId())).thenReturn(video);
        when(analysisJobRepository.save(any(AnalysisJob.class))).thenAnswer(invocation -> {
            AnalysisJob saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        var response = withAfterCommitResult(() -> service().start(owner, video.getId()));

        assertThat(response.jobId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo("QUEUED");
        verify(pipelineService).runAnalysis(100L);
    }

    @Test
    void startRejectsAnotherUsersVideo() {
        User requester = user(3L, UserRole.USER);
        when(videoFileQueryService.loadOwnedVideoForUpdate(requester, 10L))
                .thenThrow(new BusinessException(ErrorCode.VIDEO_OWNER_MISMATCH));

        assertThatThrownBy(() -> service().start(requester, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VIDEO_OWNER_MISMATCH));

        verify(analysisJobRepository, never()).save(any(AnalysisJob.class));
        verify(pipelineService, never()).runAnalysis(any());
    }

    @Test
    void startRejectsSecondActiveJobForSameVideoAndReturnsExistingJobId() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob active = job(owner, video, 100L, AnalysisStatus.PROCESSING);
        when(videoFileQueryService.loadOwnedVideoForUpdate(owner, video.getId())).thenReturn(video);
        when(analysisJobRepository.findFirstByVideoIdAndStatusInOrderByCreatedAtAsc(
                org.mockito.ArgumentMatchers.eq(video.getId()), any()))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service().start(owner, video.getId()))
                .isInstanceOfSatisfying(AnalysisAlreadyActiveException.class,
                        exception -> assertThat(exception.jobId()).isEqualTo(active.getId()));

        verify(analysisJobRepository, never()).save(any(AnalysisJob.class));
        verify(pipelineService, never()).runAnalysis(any());
    }

    @Test
    void adminDeletesDoneJobAndCleansResultJobVideoAndFiles() throws IOException {
        User admin = user(1L, UserRole.ADMIN);
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.DONE);
        createDeleteTargets(job, video);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.countByVideoId(video.getId())).thenReturn(1L);

        withAfterCommit(() -> service().deleteReport(admin, job.getId()));

        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).delete(job);
        verify(uploadedVideoRepository).delete(video);
        assertDeleteTargetsRemoved(job, video);
    }

    @Test
    void adminDeletesFailedJobEvenWhenRelatedFilesAreAlreadyMissing() {
        User admin = user(1L, UserRole.ADMIN);
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "missing.mp4", "uploads/missing.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.FAILED);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.countByVideoId(video.getId())).thenReturn(1L);

        withAfterCommit(() -> service().deleteReport(admin, job.getId()));

        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).delete(job);
        verify(uploadedVideoRepository).delete(video);
    }

    @Test
    void canceledJobCanBeDeleted() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "canceled.mp4", "uploads/canceled.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.CANCELED);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.countByVideoId(video.getId())).thenReturn(1L);

        withAfterCommit(() -> service().deleteReport(owner, job.getId()));

        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).delete(job);
        verify(uploadedVideoRepository).delete(video);
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"QUEUED", "PROCESSING", "CANCEL_REQUESTED"})
    void runningJobsCannotBeDeleted(AnalysisStatus status) {
        User admin = user(1L, UserRole.ADMIN);
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, status);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service().deleteReport(admin, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_DELETE_NOT_ALLOWED));
        verify(resultRepository, never()).deleteByJobId(job.getId());
        verify(analysisJobRepository, never()).delete(job);
    }

    @Test
    void missingJobReturnsNotFound() {
        User admin = user(1L, UserRole.ADMIN);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deleteReport(admin, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
    }

    @Test
    void regularUserCanStillDeleteOwnDoneReport() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.DONE);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(analysisJobRepository.countByVideoId(video.getId())).thenReturn(1L);

        withAfterCommit(() -> service().deleteReport(owner, job.getId()));

        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).delete(job);
        verify(uploadedVideoRepository).delete(video);
    }

    @Test
    void regularUserCannotDeleteAnotherUsersReport() {
        User requester = user(3L, UserRole.USER);
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.DONE);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service().deleteReport(requester, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        verify(resultRepository, never()).deleteByJobId(job.getId());
        verify(analysisJobRepository, never()).delete(job);
    }

    @Test
    void canceledJobCanBeRetriedWithSameJobId() throws IOException {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "canceled.mp4", "uploads/canceled.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.CANCELED);
        Files.createDirectories(storageRoot.resolve("uploads"));
        Files.writeString(storageRoot.resolve(video.getRelativePath()), "video");
        createAnalysisTargets(job);
        stubRetryLookup(owner, job);

        var response = withAfterCommitResult(() -> service().retry(owner, job.getId()));

        assertThat(response.jobId()).isEqualTo(job.getId());
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(job.getRetryCount()).isEqualTo(1);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).save(job);
        verify(pipelineService).runAnalysis(job.getId());
        assertAnalysisTargetsRemoved(job);
        assertThat(storageRoot.resolve(video.getRelativePath())).exists();
    }

    @Test
    void failedJobCanBeRetriedWithSameJobId() throws IOException {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "failed.mp4", "uploads/failed.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.FAILED);
        Files.createDirectories(storageRoot.resolve("uploads"));
        Files.writeString(storageRoot.resolve(video.getRelativePath()), "video");
        stubRetryLookup(owner, job);

        var response = withAfterCommitResult(() -> service().retry(owner, job.getId()));

        assertThat(response.jobId()).isEqualTo(job.getId());
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(job.getRetryCount()).isEqualTo(1);
        verify(resultRepository).deleteByJobId(job.getId());
        verify(analysisJobRepository).save(job);
        verify(pipelineService).runAnalysis(job.getId());
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"DONE", "QUEUED", "PROCESSING", "CANCEL_REQUESTED"})
    void nonRetryableJobsCannotBeRetried(AnalysisStatus status) {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, status);
        stubRetryLookup(owner, job);

        assertThatThrownBy(() -> service().retry(owner, job.getId()))
                .isInstanceOf(BusinessException.class);

        verify(resultRepository, never()).deleteByJobId(job.getId());
        verify(pipelineService, never()).runAnalysis(job.getId());
    }

    @Test
    void canceledJobCannotBeRetriedWhenSourceVideoIsMissing() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "missing.mp4", "uploads/missing.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.CANCELED);
        stubRetryLookup(owner, job);

        assertThatThrownBy(() -> service().retry(owner, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_SOURCE_VIDEO_MISSING));
        verify(resultRepository, never()).deleteByJobId(job.getId());
        verify(pipelineService, never()).runAnalysis(job.getId());
    }

    @Test
    void cancelQueuedJobChangesStatusToCanceled() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.QUEUED);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        var response = service().cancel(owner, job.getId());

        assertThat(response.status()).isEqualTo("CANCELED");
        verify(analysisJobRepository).save(job);
    }

    @Test
    void cancelProcessingJobChangesStatusToCancelRequested() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.PROCESSING);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        var response = service().cancel(owner, job.getId());

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");
        verify(analysisJobRepository).save(job);
    }

    @Test
    void cancelRequestedJobCanBeCancelledAgainIdempotently() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.PROCESSING);
        job.requestCancel();
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        var response = service().cancel(owner, job.getId());

        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");
        verify(analysisJobRepository, never()).save(job);
    }

    @Test
    void canceledJobCanBeCancelledAgainIdempotently() {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.QUEUED);
        job.cancel();
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        var response = service().cancel(owner, job.getId());

        assertThat(response.status()).isEqualTo("CANCELED");
        verify(analysisJobRepository, never()).save(job);
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = {"DONE", "FAILED"})
    void finishedJobsCannotBeCancelled(AnalysisStatus status) {
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, status);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service().cancel(owner, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_CANCEL_NOT_ALLOWED));
    }

    @Test
    void otherUsersJobCannotBeCancelled() {
        User requester = user(3L, UserRole.USER);
        User owner = user(2L, UserRole.USER);
        UploadedVideo video = video(owner, 10L, "video.mp4", "uploads/video.mp4");
        AnalysisJob job = job(owner, video, 100L, AnalysisStatus.QUEUED);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service().cancel(requester, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void missingJobCannotBeCancelled() {
        User owner = user(2L, UserRole.USER);
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().cancel(owner, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
    }

    private AnalysisCommandService service() {
        return new AnalysisCommandService(analysisJobRepository, resultRepository, uploadedVideoRepository,
                videoFileQueryService, pipelineService, new StorageCleanupService(storageRoot.toString()), storageRoot.toString());
    }

    private void stubRetryLookup(User owner, AnalysisJob job) {
        when(analysisJobRepository.findByIdWithVideoAndUser(job.getId())).thenReturn(Optional.of(job));
        when(videoFileQueryService.loadOwnedVideoForUpdate(owner, job.getVideo().getId())).thenReturn(job.getVideo());
        when(analysisJobRepository.findByIdWithVideoAndUserForUpdate(job.getId())).thenReturn(Optional.of(job));
    }

    private User user(Long id, UserRole role) {
        User user = User.createUser("user" + id + "@example.com", "encoded", "user" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }

    private UploadedVideo video(User owner, Long id, String storedFilename, String relativePath) {
        UploadedVideo video = new UploadedVideo(owner, storedFilename, storedFilename, relativePath, "video/mp4", 1024L);
        ReflectionTestUtils.setField(video, "id", id);
        return video;
    }

    private AnalysisJob job(User owner, UploadedVideo video, Long id, AnalysisStatus status) {
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", id);
        if (status == AnalysisStatus.DONE) {
            job.done();
        } else if (status == AnalysisStatus.FAILED) {
            job.fail("failed");
        } else if (status == AnalysisStatus.PROCESSING) {
            job.processing(AnalysisStep.PREPROCESSING, 10, "processing");
        } else if (status == AnalysisStatus.CANCEL_REQUESTED) {
            job.processing(AnalysisStep.PREPROCESSING, 10, "processing");
            job.requestCancel();
        } else if (status == AnalysisStatus.CANCELED) {
            job.cancel();
        }
        return job;
    }

    private void createDeleteTargets(AnalysisJob job, UploadedVideo video) throws IOException {
        Files.createDirectories(storageRoot.resolve("uploads"));
        Files.writeString(storageRoot.resolve(video.getRelativePath()), "video");
        Files.createDirectories(storageRoot.resolve("streams"));
        Files.writeString(storageRoot.resolve("streams").resolve("video.mp4"), "stream");
        createAnalysisTargets(job);
    }

    private void createAnalysisTargets(AnalysisJob job) throws IOException {
        Files.createDirectories(storageRoot.resolve("analysis").resolve("job-" + job.getId()));
        Files.writeString(storageRoot.resolve("analysis").resolve("job-" + job.getId()).resolve("result.json"), "{}");
        Files.writeString(storageRoot.resolve("analysis").resolve("job-" + job.getId() + ".json"), "{}");
        Files.createDirectories(storageRoot.resolve("tmp").resolve("job-" + job.getId()));
        Files.writeString(storageRoot.resolve("tmp").resolve("job-" + job.getId()).resolve("work.txt"), "tmp");
        Files.createDirectories(storageRoot.resolve("thumbnails").resolve("job-" + job.getId()));
        Files.writeString(storageRoot.resolve("thumbnails").resolve("job-" + job.getId()).resolve("thumb.jpg"), "thumb");
        Files.createDirectories(storageRoot.resolve("video-llm").resolve("job-" + job.getId()).resolve("frames"));
        Files.writeString(storageRoot.resolve("video-llm").resolve("job-" + job.getId()).resolve("frames").resolve("frame_001.jpg"), "frame");
        Files.createDirectories(storageRoot.resolve("video-llm").resolve("job-" + job.getId()).resolve("audio"));
        Files.writeString(storageRoot.resolve("video-llm").resolve("job-" + job.getId()).resolve("audio").resolve("speech.wav"), "audio");
    }

    private void assertDeleteTargetsRemoved(AnalysisJob job, UploadedVideo video) {
        List<Path> targets = List.of(
                storageRoot.resolve("analysis").resolve("job-" + job.getId()),
                storageRoot.resolve("analysis").resolve("job-" + job.getId() + ".json"),
                storageRoot.resolve("tmp").resolve("job-" + job.getId()),
                storageRoot.resolve("thumbnails").resolve("job-" + job.getId()),
                storageRoot.resolve("video-llm").resolve("job-" + job.getId()),
                storageRoot.resolve(video.getRelativePath()),
                storageRoot.resolve("streams").resolve("video.mp4")
        );
        assertThat(targets).allMatch(path -> !Files.exists(path));
    }

    private void assertAnalysisTargetsRemoved(AnalysisJob job) {
        List<Path> targets = List.of(
                storageRoot.resolve("analysis").resolve("job-" + job.getId()),
                storageRoot.resolve("analysis").resolve("job-" + job.getId() + ".json"),
                storageRoot.resolve("tmp").resolve("job-" + job.getId()),
                storageRoot.resolve("thumbnails").resolve("job-" + job.getId()),
                storageRoot.resolve("video-llm").resolve("job-" + job.getId())
        );
        assertThat(targets).allMatch(path -> !Files.exists(path));
    }

    private void withAfterCommit(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private <T> T withAfterCommitResult(java.util.function.Supplier<T> action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            T result = action.get();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            return result;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
