// 역할: 도메인 저장소와 외부 클라이언트를 조합해 실제 유스케이스 흐름을 처리하는 서비스입니다.
package com.hanium.presentation.application.result;

import com.hanium.presentation.application.analysis.AnalysisCommandService;
import com.hanium.presentation.domain.result.entity.FinalAnalysisResult;
import com.hanium.presentation.domain.result.repository.FinalAnalysisResultRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultCommandService {
    private final FinalAnalysisResultRepository resultRepository;
    private final AnalysisCommandService analysisCommandService;

    public ResultCommandService(FinalAnalysisResultRepository resultRepository,
                                AnalysisCommandService analysisCommandService) {
        this.resultRepository = resultRepository;
        this.analysisCommandService = analysisCommandService;
    }

    @Transactional
    public void delete(User user, Long resultId) {
        FinalAnalysisResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND));
        analysisCommandService.deleteReport(user, result.getJob().getId());
    }
}
