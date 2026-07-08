package com.hanium.presentation.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanium.presentation.application.analysis.AnalysisCommandService;
import com.hanium.presentation.application.auth.CurrentUserService;
import com.hanium.presentation.domain.analysis.repository.AnalysisJobRepository;
import com.hanium.presentation.domain.auth.repository.RefreshTokenRepository;
import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.repository.UserRepository;
import com.hanium.presentation.domain.user.type.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class AdminControllerTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AnalysisJobRepository analysisJobRepository = mock(AnalysisJobRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final AnalysisCommandService analysisCommandService = mock(AnalysisCommandService.class);
    private final AdminController controller = new AdminController(userRepository, analysisJobRepository,
            refreshTokenRepository, currentUserService, analysisCommandService);

    @Test
    void deleteAnalysisDelegatesToCommonDeleteService() {
        User admin = User.createUser("admin@example.com", "encoded", "admin");
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.changeRole(UserRole.ADMIN);
        when(currentUserService.currentUser()).thenReturn(admin);

        var response = controller.deleteAnalysis(100L);

        verify(analysisCommandService).deleteReport(admin, 100L);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("jobId", 100L);
    }
}
