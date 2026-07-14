// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.video;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.presentation.dto.response.VideoResponse;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoFileCommandService {
    public static final long MAX_VIDEO_BYTES = 500L * 1024L * 1024L;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska", "video/webm");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "video/mp4", ".mp4",
            "video/quicktime", ".mov",
            "video/x-msvideo", ".avi",
            "video/x-matroska", ".mkv",
            "video/webm", ".webm"
    );

    private final UploadedVideoRepository uploadedVideoRepository;
    private final StorageCleanupService storageCleanupService;
    private final Path storageRoot;

    public VideoFileCommandService(UploadedVideoRepository uploadedVideoRepository,
                                   StorageCleanupService storageCleanupService,
                                   @Value("${app.storage.root:${STORAGE_ROOT:/app/storage}}") String storageRoot) {
        this.uploadedVideoRepository = uploadedVideoRepository;
        this.storageCleanupService = storageCleanupService;
        this.storageRoot = Path.of(storageRoot).normalize();
    }

    // 함수 역할: upload에서 업로드 파일을 검증하고 저장 또는 응답 데이터를 구성합니다.
    @Transactional
    public VideoResponse upload(User user, MultipartFile file) {
        validate(file);
        String storedFilename = UUID.randomUUID() + EXTENSIONS.get(file.getContentType());
        Path uploadDir = storageRoot.resolve("uploads").normalize();
        Path target = uploadDir.resolve(storedFilename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            storageCleanupService.cleanupPartialUpload(target);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
        UploadedVideo video = new UploadedVideo(user, cleanOriginalName(file.getOriginalFilename()), storedFilename,
                "uploads/" + storedFilename, file.getContentType(), file.getSize());
        try {
            return VideoResponse.from(uploadedVideoRepository.save(video));
        } catch (RuntimeException exception) {
            storageCleanupService.cleanupPartialUpload(target);
            throw exception;
        }
    }

    // 함수 역할: validate에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_TYPE);
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.VIDEO_FILE_TOO_LARGE);
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_TYPE);
        }
    }

    // 함수 역할: cleanOriginalName에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private String cleanOriginalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "uploaded-video";
        }
        return Path.of(originalFilename).getFileName().toString().replaceAll("[\\r\\n]", "");
    }
}
