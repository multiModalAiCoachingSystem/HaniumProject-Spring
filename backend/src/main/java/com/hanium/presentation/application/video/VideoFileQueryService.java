// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.video;

import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.presentation.dto.response.VideoResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class VideoFileQueryService {
    private final UploadedVideoRepository uploadedVideoRepository;
    private final Path storageRoot;

    public VideoFileQueryService(UploadedVideoRepository uploadedVideoRepository,
                                 @Value("${app.storage.root:${STORAGE_ROOT:/app/storage}}") String storageRoot) {
        this.uploadedVideoRepository = uploadedVideoRepository;
        this.storageRoot = Path.of(storageRoot).normalize();
    }

    // 함수 역할: detail에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional(readOnly = true)
    public VideoResponse detail(User user, Long videoId) {
        return VideoResponse.from(loadOwnedVideo(user, videoId));
    }

    // 함수 역할: loadOwnedVideo에서 저장소나 외부 시스템의 데이터를 조회합니다.
    @Transactional(readOnly = true)
    public UploadedVideo loadOwnedVideo(User user, Long videoId) {
        UploadedVideo video = uploadedVideoRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_NOT_FOUND));
        if (!video.getUser().getId().equals(user.getId()) && !"ADMIN".equals(user.getRole().name())) {
            throw new BusinessException(ErrorCode.VIDEO_OWNER_MISMATCH);
        }
        return video;
    }

    // 함수 역할: resource에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public Resource resource(UploadedVideo video) {
        return streamResource(video).resource();
    }

    // 함수 역할: streamResource에서 저장된 파일을 브라우저가 받을 수 있는 응답으로 제공합니다.
    public VideoStreamResource streamResource(UploadedVideo video) {
        try {
            Path source = resolveVideoPath(video);
            if (isBrowserFriendly(video, source)) {
                return new VideoStreamResource(new UrlResource(source.toUri()), MediaType.parseMediaType(video.getMimeType()));
            }

            Path target = compatiblePath(video);
            ensureCompatibleVideo(source, target);
            return new VideoStreamResource(new UrlResource(target.toUri()), MediaType.valueOf("video/mp4"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
    }

    // 함수 역할: resolveVideoPath에서 영상 파일 정보, 변환, 분석 요청에 필요한 처리를 수행합니다.
    private Path resolveVideoPath(UploadedVideo video) {
        Path path = storageRoot.resolve(video.getRelativePath()).normalize();
        if (!path.startsWith(storageRoot) || !path.toFile().exists()) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return path;
    }

    // 함수 역할: isBrowserFriendly에서 필요한 값이나 상태를 조회해 반환합니다.
    private boolean isBrowserFriendly(UploadedVideo video, Path source) {
        String mimeType = video.getMimeType() == null ? "" : video.getMimeType().toLowerCase();
        String filename = source.getFileName().toString().toLowerCase();
        return "video/mp4".equals(mimeType) && filename.endsWith(".mp4");
    }

    // 함수 역할: compatiblePath에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private Path compatiblePath(UploadedVideo video) {
        String filename = video.getStoredFilename();
        int dot = filename.lastIndexOf('.');
        String basename = dot > 0 ? filename.substring(0, dot) : filename;
        return storageRoot.resolve("streams").resolve(basename + ".mp4").normalize();
    }

    // 함수 역할: ensureCompatibleVideo에서 영상 파일 정보, 변환, 분석 요청에 필요한 처리를 수행합니다.
    private void ensureCompatibleVideo(Path source, Path target) throws Exception {
        if (Files.exists(target) && Files.size(target) > 0 && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(source)) >= 0) {
            return;
        }
        Files.createDirectories(target.getParent());
        Process process = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-preset", "veryfast",
                "-movflags", "+faststart",
                "-c:a", "aac",
                "-b:a", "128k",
                target.toString()
        ).redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.exists(target) || Files.size(target) == 0) {
            throw new IllegalStateException("ffmpeg conversion failed: " + output);
        }
    }

    public record VideoStreamResource(Resource resource, MediaType mediaType) {
    }
}
