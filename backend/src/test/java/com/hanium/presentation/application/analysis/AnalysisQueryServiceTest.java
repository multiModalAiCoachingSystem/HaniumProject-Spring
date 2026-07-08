package com.hanium.presentation.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisQueryServiceTest {
    private final AnalysisJobRepository analysisJobRepository = Mockito.mock(AnalysisJobRepository.class);
    private final FinalAnalysisResultRepository resultRepository = Mockito.mock(FinalAnalysisResultRepository.class);
    private final AnalysisQueryService queryService = new AnalysisQueryService(analysisJobRepository, resultRepository);

    @Test
    void ownerCanReadAnalysisStatus() {
        User owner = user(1L, UserRole.USER);
        AnalysisJob job = job(owner, 100L, AnalysisStatus.QUEUED);
        when(analysisJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        var response = queryService.status(owner, job.getId());

        assertThat(response.jobId()).isEqualTo(job.getId());
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.videoId()).isEqualTo(job.getVideo().getId());
    }

    @Test
    void otherUserCannotReadAnalysisStatus() {
        User owner = user(1L, UserRole.USER);
        User requester = user(2L, UserRole.USER);
        AnalysisJob job = job(owner, 100L, AnalysisStatus.DONE);
        when(analysisJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> queryService.status(requester, job.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void missingAnalysisStatusReturnsNotFound() {
        User owner = user(1L, UserRole.USER);
        when(analysisJobRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.status(owner, 404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
    }

    private AnalysisJob job(User owner, Long id, AnalysisStatus status) {
        UploadedVideo video = new UploadedVideo(owner, "video.mp4", "video.mp4", "uploads/video.mp4", "video/mp4", 1024L);
        ReflectionTestUtils.setField(video, "id", 10L);
        AnalysisJob job = new AnalysisJob(owner, video);
        ReflectionTestUtils.setField(job, "id", id);
        if (status == AnalysisStatus.DONE) {
            job.done();
        } else if (status == AnalysisStatus.FAILED) {
            job.fail("failed");
        } else if (status == AnalysisStatus.PROCESSING) {
            job.processing(com.hanium.presentation.domain.analysis.type.AnalysisStep.PREPROCESSING, 10, "processing");
        } else if (status == AnalysisStatus.CANCEL_REQUESTED) {
            job.processing(com.hanium.presentation.domain.analysis.type.AnalysisStep.PREPROCESSING, 10, "processing");
            job.requestCancel();
        } else if (status == AnalysisStatus.CANCELED) {
            job.cancel();
        }
        return job;
    }

    private User user(Long id, UserRole role) {
        User user = User.createUser("user" + id + "@example.com", "encoded", "user" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }
}
