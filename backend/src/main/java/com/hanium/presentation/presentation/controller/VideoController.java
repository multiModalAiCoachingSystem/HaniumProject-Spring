// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.application.video.VideoFileCommandService;
import com.hanium.presentation.application.video.VideoFileQueryService;
import com.hanium.presentation.domain.video.entity.UploadedVideo;
import com.hanium.presentation.global.response.ApiResponse;
import com.hanium.presentation.presentation.dto.response.VideoResponse;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
@SuppressWarnings("null")
public class VideoController {
    private final CurrentUserService currentUserService;
    private final VideoFileCommandService commandService;
    private final VideoFileQueryService queryService;

    // 함수 역할: VideoController에서 영상 파일 정보, 변환, 분석 요청에 필요한 처리를 수행합니다.
    public VideoController(CurrentUserService currentUserService, VideoFileCommandService commandService, VideoFileQueryService queryService) {
        this.currentUserService = currentUserService;
        this.commandService = commandService;
        this.queryService = queryService;
    }

    // 함수 역할: upload에서 업로드 파일을 검증하고 저장 또는 응답 데이터를 구성합니다.
    @PostMapping
    public ApiResponse<VideoResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(commandService.upload(currentUserService.currentUser(), file), "영상이 업로드되었습니다.");
    }

    // 함수 역할: detail에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @GetMapping("/{videoId}")
    public ApiResponse<VideoResponse> detail(@PathVariable Long videoId) {
        return ApiResponse.ok(queryService.detail(currentUserService.currentUser(), videoId), "영상 정보입니다.");
    }

    // 함수 역할: stream에서 저장된 파일을 브라우저가 받을 수 있는 응답으로 제공합니다.
    @GetMapping("/{videoId}/stream")
    public ResponseEntity<?> stream(@PathVariable Long videoId, @RequestHeader HttpHeaders headers) throws Exception {
        UploadedVideo video = queryService.loadOwnedVideo(currentUserService.currentUser(), videoId);
        VideoFileQueryService.VideoStreamResource stream = queryService.streamResource(video);
        Resource resource = stream.resource();
        MediaType mediaType = stream.mediaType();
        List<HttpRange> ranges = headers.getRange();
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .body(resource);
        }
        ResourceRegion region = ranges.get(0).toResourceRegion(resource);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .body(region);
    }
}
