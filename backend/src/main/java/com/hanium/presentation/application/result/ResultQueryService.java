// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.presentation.dto.response.ResultDetailResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class ResultQueryService {
    private final FinalAnalysisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    // 함수 역할: ResultQueryService에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    public ResultQueryService(FinalAnalysisResultRepository resultRepository, ObjectMapper objectMapper) {
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    // 함수 역할: detailByResultId에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional(readOnly = true)
    public ResultDetailResponse detailByResultId(User user, Long resultId) {
        return toResponse(loadOwnedResult(user, resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND))));
    }

    // 함수 역할: detailByJobId에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional(readOnly = true)
    public ResultDetailResponse detailByJobId(User user, Long jobId) {
        return toResponse(loadOwnedResult(user, resultRepository.findByJobId(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND))));
    }

    // 함수 역할: list에서 저장소나 외부 시스템의 데이터를 조회합니다.
    @Transactional(readOnly = true)
    public List<ResultDetailResponse> list(User user) {
        return resultRepository.findByJob_User_IdOrderByCreatedAtDesc(user.getId()).stream().map(this::toResponse).toList();
    }

    // 함수 역할: loadOwnedResult에서 저장소나 외부 시스템의 데이터를 조회합니다.
    private FinalAnalysisResult loadOwnedResult(User user, FinalAnalysisResult result) {
        if (!result.getUser().getId().equals(user.getId()) && !"ADMIN".equals(user.getRole().name())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return result;
    }

    // 함수 역할: toResponse에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    private ResultDetailResponse toResponse(FinalAnalysisResult result) {
        try {
            List<Map<String, Object>> scores = objectMapper.readValue(
                    result.getScoresJson(), new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> feedback = objectMapper.readValue(
                    result.getFeedbackJson(), new TypeReference<List<Map<String, Object>>>() {});
            Map<String, Object> summary = objectMapper.readValue(
                    result.getSummaryJson(), new TypeReference<Map<String, Object>>() {});
            return new ResultDetailResponse(
                    result.getId(),
                    result.getJob().getId(),
                    result.getVideo().getId(),
                    "발표 분석 결과",
                    result.getCreatedAt(),
                    result.getVideo().getOriginalFilename(),
                    result.getVideo().getDurationSeconds(),
                    result.getTotalScore(),
                    scores,
                    feedback,
                    summary,
                    asMap(summary.get("metrics")),
                    asMap(summary.get("videoLlmMetadata")),
                    feedback
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.RESULT_SAVE_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            return (Map<String, Object>) source;
        }
        return Map.of();
    }
}
