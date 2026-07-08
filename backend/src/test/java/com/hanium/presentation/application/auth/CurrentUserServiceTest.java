package com.hanium.presentation.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hanium.presentation.domain.user.entity.User;
import com.hanium.presentation.domain.user.repository.UserRepository;
import com.hanium.presentation.global.exception.BusinessException;
import com.hanium.presentation.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserServiceTest {
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final CurrentUserService currentUserService = new CurrentUserService(userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserReturnsAuthenticatedUser() {
        User user = User.createUser("owner@example.com", "encoded", "owner");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("owner@example.com", null, java.util.List.of()));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));

        assertThat(currentUserService.currentUser()).isSameAs(user);
    }

    @Test
    void currentUserRejectsUnauthenticatedAccess() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(currentUserService::currentUser)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
    }
}
