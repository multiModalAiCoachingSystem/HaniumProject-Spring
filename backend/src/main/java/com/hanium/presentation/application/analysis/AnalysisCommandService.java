// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.analysis;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.application.video.VideoFileQueryService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.AnalysisAlreadyActiveException;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.presentation.dto.response.AnalysisCancelResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisRetryResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisStartResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AnalysisCommandService {
    private static final List<AnalysisStatus> ACTIVE_STATUSES = List.of(
            AnalysisStatus.QUEUED,
            AnalysisStatus.PROCESSING,
            AnalysisStatus.CANCEL_REQUESTED
    );
    private final AnalysisJobRepository analysisJobRepository;
    private final FinalAnalysisResultRepository resultRepository;
    private final UploadedVideoRepository uploadedVideoRepository;
    private final VideoFileQueryService videoFileQueryService;
    private final AnalysisPipelineService pipelineService;
    private final StorageCleanupService storageCleanupService;
    private final Path storageRoot;

    public AnalysisCommandService(AnalysisJobRepository analysisJobRepository,
                                  FinalAnalysisResultRepository resultRepository,
                                  UploadedVideoRepository uploadedVideoRepository,
                                  VideoFileQueryService videoFileQueryService,
                                  AnalysisPipelineService pipelineService,
                                  StorageCleanupService storageCleanupService,
                                  @Value("${app.storage.root:${STORAGE_ROOT:/app/storage}}") String storageRoot) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
        this.uploadedVideoRepository = uploadedVideoRepository;
        this.videoFileQueryService = videoFileQueryService;
        this.pipelineService = pipelineService;
        this.storageCleanupService = storageCleanupService;
        this.storageRoot = Path.of(storageRoot).normalize();
    }

    // 함수 역할: start에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional
    public AnalysisStartResponse start(User user, Long videoId) {
        UploadedVideo video = videoFileQueryService.loadOwnedVideoForUpdate(user, videoId);
        rejectWhenActiveJobExists(video.getId(), null);
        AnalysisJob job = analysisJobRepository.save(new AnalysisJob(user, video));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            // 함수 역할: afterCommit에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
            @Override
            public void afterCommit() {
                pipelineService.runAnalysis(job.getId());
            }
        });
        return new AnalysisStartResponse(job.getId(), job.getStatus().name());
    }

    @Transactional
    public AnalysisCancelResponse cancel(User requester, Long jobId) {
        AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUserForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));

        if (!job.getUser().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (job.getStatus() == AnalysisStatus.QUEUED) {
            job.cancel();
            analysisJobRepository.save(job);
            return new AnalysisCancelResponse(job.getId(), job.getStatus().name(), "분석이 취소되었습니다.");
        }

        if (job.getStatus() == AnalysisStatus.PROCESSING) {
            job.requestCancel();
            analysisJobRepository.save(job);
            return new AnalysisCancelResponse(job.getId(), job.getStatus().name(), "분석 중단을 요청했습니다.");
        }

        if (job.getStatus() == AnalysisStatus.CANCEL_REQUESTED) {
            return new AnalysisCancelResponse(job.getId(), job.getStatus().name(), "분석 중단을 요청했습니다.");
        }

        if (job.getStatus() == AnalysisStatus.CANCELED) {
            return new AnalysisCancelResponse(job.getId(), job.getStatus().name(), "분석이 취소되었습니다.");
        }

        throw new BusinessException(ErrorCode.ANALYSIS_CANCEL_NOT_ALLOWED);
    }

    // 함수 역할: 실패한 분석 작업을 초기화하고 같은 jobId로 분석 파이프라인을 다시 실행합니다.
    @Transactional
    public AnalysisRetryResponse retry(User requester, Long jobId) {
        AnalysisJob snapshot = analysisJobRepository.findByIdWithVideoAndUser(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
        if (!snapshot.getUser().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        videoFileQueryService.loadOwnedVideoForUpdate(requester, snapshot.getVideo().getId());
        AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUserForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
        if (!job.getUser().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (job.getStatus() == AnalysisStatus.QUEUED || job.getStatus() == AnalysisStatus.PROCESSING
                || job.getStatus() == AnalysisStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }

        if (job.getStatus() != AnalysisStatus.FAILED && job.getStatus() != AnalysisStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ANALYSIS_RETRY_NOT_ALLOWED);
        }

        rejectWhenActiveJobExists(job.getVideo().getId(), job.getId());

        UploadedVideo video = job.getVideo();
        Path sourceVideo = storageRoot.resolve(video.getRelativePath()).normalize();
        if (!sourceVideo.startsWith(storageRoot) || !Files.exists(sourceVideo)) {
            throw new BusinessException(ErrorCode.ANALYSIS_SOURCE_VIDEO_MISSING);
        }

        resultRepository.deleteByJobId(job.getId());
        job.retry();
        analysisJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            // 함수 역할: afterCommit에서 재시도 상태 저장 후 분석 파이프라인을 실행합니다.
            @Override
            public void afterCommit() {
                storageCleanupService.cleanupBeforeRetry(job);
                pipelineService.runAnalysis(job.getId());
            }
        });
        return new AnalysisRetryResponse(job.getId(), job.getStatus().name(), "분석을 다시 시작했습니다.");
    }

    private void rejectWhenActiveJobExists(Long videoId, Long allowedJobId) {
        analysisJobRepository.findFirstByVideoIdAndStatusInOrderByCreatedAtAsc(videoId, ACTIVE_STATUSES)
                .filter(active -> allowedJobId == null || !active.getId().equals(allowedJobId))
                .ifPresent(active -> {
                    throw new AnalysisAlreadyActiveException(active.getId());
                });
    }

    @Transactional
    public void deleteReport(User requester, Long jobId) {
        AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUserForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));

        if (!job.getUser().getId().equals(requester.getId()) && requester.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (job.getStatus() != AnalysisStatus.DONE
                && job.getStatus() != AnalysisStatus.FAILED
                && job.getStatus() != AnalysisStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ANALYSIS_DELETE_NOT_ALLOWED);
        }

        UploadedVideo video = job.getVideo();
        boolean deleteVideo = analysisJobRepository.countByVideoId(video.getId()) <= 1;
        resultRepository.deleteByJobId(job.getId());
        analysisJobRepository.delete(job);
        if (deleteVideo) {
            uploadedVideoRepository.delete(video);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageCleanupService.cleanupReportFiles(job, deleteVideo);
            }
        });
    }

}
