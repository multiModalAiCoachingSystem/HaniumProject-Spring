package com.hanium.presentation.application.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hanium.presentation.application.storage.StorageCleanupService;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.domain.video.repository.UploadedVideoRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

class VideoFileCommandServiceTest {
    private final UploadedVideoRepository uploadedVideoRepository = mock(UploadedVideoRepository.class);

    @TempDir
    Path storageRoot;

    @Test
    void uploadFailureCleansPartialFile() throws Exception {
        MultipartFile file = videoFile(new FailingInputStream());

        assertThatThrownBy(() -> service().upload(user(), file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));

        assertUploadsDirectoryEmpty();
    }

    @Test
    void databaseFailureAfterFileCopyCleansStoredFile() throws Exception {
        MultipartFile file = videoFile(new ByteArrayInputStream("video".getBytes()));
        when(uploadedVideoRepository.save(any(UploadedVideo.class))).thenThrow(new RuntimeException("db failed"));

        assertThatThrownBy(() -> service().upload(user(), file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db failed");

        assertUploadsDirectoryEmpty();
    }

    private VideoFileCommandService service() {
        return new VideoFileCommandService(uploadedVideoRepository,
                new StorageCleanupService(storageRoot.toString()), storageRoot.toString());
    }

    private User user() {
        return User.createUser("owner@example.com", "encoded", "owner");
    }

    private MultipartFile videoFile(InputStream inputStream) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.getOriginalFilename()).thenReturn("video.mp4");
        when(file.getInputStream()).thenReturn(inputStream);
        return file;
    }

    private void assertUploadsDirectoryEmpty() throws IOException {
        Path uploadDir = storageRoot.resolve("uploads");
        if (!Files.exists(uploadDir)) {
            return;
        }
        try (var paths = Files.list(uploadDir)) {
            assertThat(paths).isEmpty();
        }
    }

    private static final class FailingInputStream extends InputStream {
        private boolean firstRead = true;

        @Override
        public int read() throws IOException {
            if (firstRead) {
                firstRead = false;
                return 'x';
            }
            throw new IOException("client disconnected");
        }
    }
}
