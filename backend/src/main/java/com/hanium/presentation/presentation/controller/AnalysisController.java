// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.application.analysis.AnalysisCommandService;
import com.hanium.presentation.application.analysis.AnalysisQueryService;
import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.application.result.ResultQueryService;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.global.response.PageResponse;
import com.hanium.presentation.global.response.ApiResponse;
import com.hanium.presentation.presentation.dto.request.AnalysisRunRequest;
import com.hanium.presentation.presentation.dto.response.AnalysisCancelResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisReportResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisRetryResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisStartResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisStatusResponse;
import com.hanium.presentation.presentation.dto.response.ResultDetailResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {
    private final CurrentUserService currentUserService;
    private final AnalysisCommandService commandService;
    private final AnalysisQueryService queryService;
    private final ResultQueryService resultQueryService;

    public AnalysisController(CurrentUserService currentUserService, AnalysisCommandService commandService,
                              AnalysisQueryService queryService, ResultQueryService resultQueryService) {
        this.currentUserService = currentUserService;
        this.commandService = commandService;
        this.queryService = queryService;
        this.resultQueryService = resultQueryService;
    }

    // 함수 역할: start에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @PostMapping
    public ApiResponse<AnalysisStartResponse> start(@Valid @RequestBody AnalysisRunRequest request) {
        return ApiResponse.ok(commandService.start(currentUserService.currentUser(), request.videoId()), "분석 작업이 생성되었습니다.");
    }

    // 함수 역할: 현재 사용자의 분석 작업 상태 목록을 반환합니다.
    @GetMapping
    public ApiResponse<List<AnalysisStatusResponse>> list() {
        return ApiResponse.ok(queryService.list(currentUserService.currentUser()), "분석 작업 목록입니다.");
    }

    @GetMapping("/reports")
    public ApiResponse<PageResponse<AnalysisReportResponse>> reports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) AnalysisStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ApiResponse.ok(
                queryService.reports(currentUserService.currentUser(), page, size, status, keyword, sort),
                "리포트 목록입니다."
        );
    }

    // 함수 역할: status에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @GetMapping("/{jobId}/status")
    public ApiResponse<AnalysisStatusResponse> status(@PathVariable Long jobId) {
        return ApiResponse.ok(queryService.status(currentUserService.currentUser(), jobId), "분석 상태입니다.");
    }

    // 함수 역할: 현재 사용자의 실패한 분석 작업을 다시 실행합니다.
    @PostMapping("/{jobId}/retry")
    public ApiResponse<AnalysisRetryResponse> retry(@PathVariable Long jobId) {
        return ApiResponse.ok(commandService.retry(currentUserService.currentUser(), jobId), "분석을 다시 시작했습니다.");
    }

    @PostMapping("/{jobId}/cancel")
    public ApiResponse<AnalysisCancelResponse> cancel(@PathVariable Long jobId) {
        return ApiResponse.ok(commandService.cancel(currentUserService.currentUser(), jobId), "분석 취소 요청이 처리되었습니다.");
    }

    @DeleteMapping("/{jobId}")
    public ApiResponse<Void> delete(@PathVariable Long jobId) {
        commandService.deleteReport(currentUserService.currentUser(), jobId);
        return ApiResponse.ok(null, "리포트가 삭제되었습니다.");
    }

    // 함수 역할: result에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @GetMapping("/{jobId}/result")
    public ApiResponse<ResultDetailResponse> result(@PathVariable Long jobId) {
        return ApiResponse.ok(resultQueryService.detailByJobId(currentUserService.currentUser(), jobId), "분석 결과입니다.");
    }
}
