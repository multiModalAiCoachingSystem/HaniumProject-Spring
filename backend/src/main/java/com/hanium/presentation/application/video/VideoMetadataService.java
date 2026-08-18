package com.hanium.presentation.application.video;

import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class VideoMetadataService {
    private static final long FFPROBE_TIMEOUT_SECONDS = 10;

    public double durationSeconds(Path videoPath) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    videoPath.toString()
            ).redirectErrorStream(true).start();

            if (!process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
            }

            String firstLine = output.lines().findFirst().orElse("").trim();
            double duration = Double.parseDouble(firstLine);
            if (!Double.isFinite(duration) || duration <= 0) {
                throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
            }
            return duration;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_METADATA);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
