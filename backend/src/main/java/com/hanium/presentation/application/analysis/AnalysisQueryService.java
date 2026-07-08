// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.analysis;

import com.hanium.presentation.domain.analysis.entity.AnalysisJob;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.analysis.type.AnalysisStatus;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.type.UserRole;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import com.hanium.presentation.global.response.PageResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisReportResponse;
import com.hanium.presentation.presentation.dto.response.AnalysisStatusResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("null")
public class AnalysisQueryService {
    private static final int DEFAULT_REPORT_PAGE_SIZE = 10;
    private static final int MAX_REPORT_PAGE_SIZE = 100;
    private static final EnumSet<AnalysisStatus> REPORT_STATUSES = EnumSet.of(
            AnalysisStatus.QUEUED,
            AnalysisStatus.PROCESSING,
            AnalysisStatus.CANCEL_REQUESTED,
            AnalysisStatus.CANCELED,
            AnalysisStatus.DONE,
            AnalysisStatus.FAILED
    );

    private final AnalysisJobRepository analysisJobRepository;
    private final FinalAnalysisResultRepository resultRepository;

    // 함수 역할: AnalysisQueryService에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    public AnalysisQueryService(AnalysisJobRepository analysisJobRepository,
                                FinalAnalysisResultRepository resultRepository) {
        this.analysisJobRepository = analysisJobRepository;
        this.resultRepository = resultRepository;
    }

    // 함수 역할: status에서 도메인 처리에 필요한 비즈니스 로직을 수행합니다.
    @Transactional(readOnly = true)
    public AnalysisStatusResponse status(User user, Long jobId) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
        if (!job.getUser().getId().equals(user.getId()) && !"ADMIN".equals(user.getRole().name())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return AnalysisStatusResponse.from(job);
    }

    // 함수 역할: 현재 사용자의 분석 작업 상태 목록을 최신순으로 반환합니다.
    @Transactional(readOnly = true)
    public List<AnalysisStatusResponse> list(User user) {
        return analysisJobRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(AnalysisStatusResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalysisReportResponse> reports(User user, int page, int size, AnalysisStatus status,
                                                        String keyword, String sort) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), normalizeSize(size), reportSort(sort));
        var reportPage = analysisJobRepository.findAll(reportSpecification(user, status, keyword), pageRequest);
        List<Long> jobIds = reportPage.getContent().stream().map(AnalysisJob::getId).toList();
        Map<Long, FinalAnalysisResult> resultsByJobId = resultRepository.findByJob_IdIn(jobIds).stream()
                .collect(Collectors.toMap(result -> result.getJob().getId(), Function.identity()));

        return PageResponse.from(reportPage.map(job -> AnalysisReportResponse.from(job, resultsByJobId.get(job.getId()))));
    }

    private Specification<AnalysisJob> reportSpecification(User user, AnalysisStatus status, String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            var video = root.join("video", JoinType.INNER);

            if (status == null) {
                predicates.add(root.get("status").in(REPORT_STATUSES));
            } else if (REPORT_STATUSES.contains(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            } else {
                predicates.add(criteriaBuilder.disjunction());
            }

            if (user.getRole() != UserRole.ADMIN) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), user.getId()));
            }

            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(video.get("originalFilename")), likeKeyword));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_REPORT_PAGE_SIZE;
        }
        return Math.min(size, MAX_REPORT_PAGE_SIZE);
    }

    private Sort reportSort(String sort) {
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && sort.toLowerCase().contains("asc")) {
            direction = Sort.Direction.ASC;
        }
        return Sort.by(direction, "createdAt").and(Sort.by(direction, "id"));
    }
}
