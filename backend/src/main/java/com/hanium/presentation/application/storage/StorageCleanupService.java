package com.hanium.presentation.application.storage;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
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

@Service
public class StorageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupService.class);

    private final Path storageRoot;

    public StorageCleanupService(@Value("${app.storage.root:${STORAGE_ROOT:/data}}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).normalize();
    }

    public void cleanupPartialUpload(Path path) {
        deleteTarget(safePath(path), "partial-upload", null, null, false);
    }

    public void cleanupAnalysisArtifacts(Long jobId) {
        cleanupTargets(analysisArtifactTargets(jobId), "analysis-artifact", jobId, null, true);
    }

    public void cleanupBeforeRetry(AnalysisJob job) {
        cleanupTargets(analysisArtifactTargets(job.getId()), "retry-artifact", job.getId(), job.getVideo().getId(), true);
    }

    public void cleanupReportFiles(AnalysisJob job, boolean deleteVideo) {
        UploadedVideo video = job.getVideo();
        List<Path> targets = new ArrayList<>(analysisArtifactTargets(job.getId()));
        if (deleteVideo) {
            targets.add(resolveRelative(video.getRelativePath()));
            targets.add(compatibleVideoPath(video));
        }
        cleanupTargets(targets, "report-file", job.getId(), video.getId(), true);
    }

    private List<Path> analysisArtifactTargets(Long jobId) {
        return List.of(
                storageRoot.resolve("analysis").resolve("job-" + jobId).normalize(),
                storageRoot.resolve("analysis").resolve("job-" + jobId + ".json").normalize(),
                storageRoot.resolve("tmp").resolve("job-" + jobId).normalize(),
                storageRoot.resolve("thumbnails").resolve("job-" + jobId).normalize(),
                storageRoot.resolve("video-llm").resolve("job-" + jobId).normalize()
        );
    }

    private Path resolveRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return storageRoot.resolve(relativePath).normalize();
    }

    private Path compatibleVideoPath(UploadedVideo video) {
        String filename = video.getStoredFilename();
        int dot = filename.lastIndexOf('.');
        String basename = dot > 0 ? filename.substring(0, dot) : filename;
        return storageRoot.resolve("streams").resolve(basename + ".mp4").normalize();
    }

    private void cleanupTargets(List<Path> targets, String kind, Long jobId, Long videoId, boolean warnMissing) {
        targets.stream()
                .map(this::safePath)
                .filter(path -> path != null && path.startsWith(storageRoot))
                .distinct()
                .forEach(path -> deleteTarget(path, kind, jobId, videoId, warnMissing));
    }

    private Path safePath(Path path) {
        return path == null ? null : path.normalize();
    }

    private void deleteTarget(Path target, String kind, Long jobId, Long videoId, boolean warnMissing) {
        if (target == null || !target.startsWith(storageRoot)) {
            log.warn("Storage cleanup skipped unsafe path. kind={} jobId={} videoId={}", kind, jobId, videoId);
            return;
        }
        try {
            if (!Files.exists(target)) {
                if (warnMissing) {
                    log.warn("Storage cleanup target already missing. kind={} jobId={} videoId={} path={}",
                            kind, jobId, videoId, target);
                }
                return;
            }
            if (Files.isDirectory(target)) {
                try (var paths = Files.walk(target)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> deleteFileIfExists(path, kind, jobId, videoId));
                }
            } else {
                deleteFileIfExists(target, kind, jobId, videoId);
            }
        } catch (IOException exception) {
            log.warn("Storage cleanup failed. kind={} jobId={} videoId={} path={} message={}",
                    kind, jobId, videoId, target, exception.getMessage());
        }
    }

    private void deleteFileIfExists(Path path, String kind, Long jobId, Long videoId) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Storage cleanup failed. kind={} jobId={} videoId={} path={} message={}",
                    kind, jobId, videoId, path, exception.getMessage());
        }
    }
}
