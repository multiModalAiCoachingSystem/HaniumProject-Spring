// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.analysis;

import com.hanium.presentation.application.video.VideoFileQueryService;
import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.presentation.dto.response.AnalysisCancelResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisRetryResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisStartResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AnalysisCommandService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisCommandService.class);

    private final AnalysisJobRepository analysisJobRepository;
    private final FinalAnalysisResultRepository resultRepository;
    private final UploadedVideoRepository uploadedVideoRepository;
    private final VideoFileQueryService videoFileQueryService;
    private final AnalysisPipelineService pipelineService;
    private final Path storageRoot;

    public AnalysisCommandService(AnalysisJobRepository analysisJobRepository,
                                  FinalAnalysisResultRepository resultRepository,
                                  UploadedVideoRepository uploadedVideoRepository,
                                  VideoFileQueryService videoFileQueryService,
                                  AnalysisPipelineService pipelineService,
                                  @Value("${app.storage.root:${STORAGE_ROOT:/data}}") String storageRoot) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
        this.uploadedVideoRepository = uploadedVideoRepository;
        this.videoFileQueryService = videoFileQueryService;
        this.pipelineService = pipelineService;
        this.storageRoot = Path.of(storageRoot).normalize();
    }

    // 함수 역할: start에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional
    public AnalysisStartResponse start(User user, Long videoId) {
        UploadedVideo video = videoFileQueryService.loadOwnedVideo(user, videoId);
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
        AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUserForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));

        if (!job.getUser().getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (job.getStatus() == AnalysisStatus.QUEUED || job.getStatus() == AnalysisStatus.PROCESSING
                || job.getStatus() == AnalysisStatus.CANCEL_REQUESTED) {
            throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        }

        // TODO: CANCELED 상태 재분석 정책은 프론트 리포트 보관함 확장 작업에서 확정한다.
        if (job.getStatus() != AnalysisStatus.FAILED) {
            throw new BusinessException(ErrorCode.ANALYSIS_RETRY_NOT_ALLOWED);
        }

        UploadedVideo video = job.getVideo();
        Path sourceVideo = storageRoot.resolve(video.getRelativePath()).normalize();
        if (!sourceVideo.startsWith(storageRoot) || !Files.exists(sourceVideo)) {
            throw new BusinessException(ErrorCode.ANALYSIS_SOURCE_VIDEO_MISSING);
        }

        List<Path> cleanupTargets = retryCleanupTargets(job);
        resultRepository.deleteByJobId(job.getId());
        job.retry();
        analysisJobRepository.save(job);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            // 함수 역할: afterCommit에서 재시도 상태 저장 후 분석 파이프라인을 실행합니다.
            @Override
            public void afterCommit() {
                deleteFiles(cleanupTargets);
                pipelineService.runAnalysis(job.getId());
            }
        });
        return new AnalysisRetryResponse(job.getId(), job.getStatus().name(), "분석을 다시 시작했습니다.");
    }

    @Transactional
    public void deleteReport(User requester, Long jobId) {
        AnalysisJob job = analysisJobRepository.findByIdWithVideoAndUser(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));

        if (!job.getUser().getId().equals(requester.getId()) && requester.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // TODO: CANCELED 삭제 허용은 프론트 리포트 보관함과 관리자 삭제 정책 확장 작업에서 처리한다.
        if (job.getStatus() != AnalysisStatus.DONE && job.getStatus() != AnalysisStatus.FAILED) {
            throw new BusinessException(ErrorCode.ANALYSIS_DELETE_NOT_ALLOWED);
        }

        UploadedVideo video = job.getVideo();
        boolean deleteVideo = analysisJobRepository.countByVideoId(video.getId()) <= 1;
        List<Path> deleteTargets = fileDeleteTargets(job, video, deleteVideo);

        resultRepository.deleteByJobId(job.getId());
        analysisJobRepository.delete(job);
        if (deleteVideo) {
            uploadedVideoRepository.delete(video);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFiles(deleteTargets);
            }
        });
    }

    private List<Path> fileDeleteTargets(AnalysisJob job, UploadedVideo video, boolean deleteVideo) {
        List<Path> targets = new ArrayList<>();
        targets.add(storageRoot.resolve("analysis").resolve("job-" + job.getId()).normalize());
        targets.add(storageRoot.resolve("analysis").resolve("job-" + job.getId() + ".json").normalize());
        targets.add(storageRoot.resolve("tmp").resolve("job-" + job.getId()).normalize());
        targets.add(storageRoot.resolve("thumbnails").resolve("job-" + job.getId()).normalize());

        if (deleteVideo) {
            targets.add(storageRoot.resolve(video.getRelativePath()).normalize());
            targets.add(compatibleVideoPath(video));
        }
        return targets.stream()
                .filter(path -> path.startsWith(storageRoot))
                .distinct()
                .toList();
    }

    private List<Path> retryCleanupTargets(AnalysisJob job) {
        List<Path> targets = new ArrayList<>();
        targets.add(storageRoot.resolve("analysis").resolve("job-" + job.getId()).normalize());
        targets.add(storageRoot.resolve("analysis").resolve("job-" + job.getId() + ".json").normalize());
        targets.add(storageRoot.resolve("tmp").resolve("job-" + job.getId()).normalize());
        targets.add(storageRoot.resolve("thumbnails").resolve("job-" + job.getId()).normalize());
        return targets.stream()
                .filter(path -> path.startsWith(storageRoot))
                .distinct()
                .toList();
    }

    private Path compatibleVideoPath(UploadedVideo video) {
        String filename = video.getStoredFilename();
        int dot = filename.lastIndexOf('.');
        String basename = dot > 0 ? filename.substring(0, dot) : filename;
        return storageRoot.resolve("streams").resolve(basename + ".mp4").normalize();
    }

    private void deleteFiles(List<Path> targets) {
        for (Path target : targets) {
            try {
                if (!Files.exists(target)) {
                    log.warn("Report file already missing. path={}", target);
                    continue;
                }
                if (Files.isDirectory(target)) {
                    try (var paths = Files.walk(target)) {
                        paths.sorted(Comparator.reverseOrder()).forEach(this::deleteFileIfExists);
                    }
                } else {
                    deleteFileIfExists(target);
                }
            } catch (IOException exception) {
                log.warn("Report file cleanup failed. path={}", target, exception);
            }
        }
    }

    private void deleteFileIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Report file cleanup failed. path={}", path, exception);
        }
    }
}
