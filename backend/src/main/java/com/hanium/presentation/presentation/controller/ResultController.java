// 역할: HTTP 요청을 받아 애플리케이션 서비스로 전달하고 API 응답을 구성하는 컨트롤러입니다.
package com.hanium.presentation.presentation.controller;

import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.application.result.ResultCommandService;
import com.hanium.presentation.application.result.ResultQueryService;
import com.hanium.presentation.global.response.ApiResponse;
import com.hanium.presentation.presentation.dto.response.ResultDetailResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/results")
public class ResultController {
    private final CurrentUserService currentUserService;
    private final ResultQueryService resultQueryService;
    private final ResultCommandService resultCommandService;

    // 함수 역할: ResultController에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    public ResultController(CurrentUserService currentUserService, ResultQueryService resultQueryService,
                            ResultCommandService resultCommandService) {
        this.currentUserService = currentUserService;
        this.resultQueryService = resultQueryService;
        this.resultCommandService = resultCommandService;
    }

    // 함수 역할: list에서 저장소나 외부 시스템의 데이터를 조회합니다.
    @GetMapping
    public ApiResponse<List<ResultDetailResponse>> list() {
        return ApiResponse.ok(resultQueryService.list(currentUserService.currentUser()), "분석 결과 목록입니다.");
    }

    // 함수 역할: detail에서 클라이언트 요청을 받아 서비스 호출 결과를 응답으로 반환합니다.
    @GetMapping("/{resultId}")
    public ApiResponse<ResultDetailResponse> detail(@PathVariable Long resultId) {
        return ApiResponse.ok(resultQueryService.detailByResultId(currentUserService.currentUser(), resultId), "분석 결과입니다.");
    }

    @DeleteMapping("/{resultId}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long resultId) {
        resultCommandService.delete(currentUserService.currentUser(), resultId);
        return ApiResponse.ok(Map.<String, Object>of("resultId", resultId), "리포트가 삭제되었습니다.");
    }
}
