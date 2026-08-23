package com.farmbroker.farmbroker.auth.service;

import com.farmbroker.farmbroker.auth.dto.SignupRequest;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.security.JwtTokenProvider;
import com.farmbroker.farmbroker.user.domain.User;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 회원가입이 이메일 인증을 실제로 강제하는지 검증한다.
// 프론트를 우회해 POST /auth/signup을 직접 호출하는 경로가 막혀야 하므로 서버 쪽 재확인이 핵심이다.
@ExtendWith(MockitoExtension.class)
class AuthServiceSignupVerificationTest {

    private static final String EMAIL = "farmer@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    private static SignupRequest request() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", EMAIL);
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "nickname", "도시농부");
        return request;
    }

    @Test
    @DisplayName("이메일 인증을 마치지 않으면 EMAIL_NOT_VERIFIED로 가입이 막힌다")
    void signup_withoutVerification_isBlocked() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        willThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .given(emailVerificationService).consumeVerified(EMAIL);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증을 마쳤으면 가입되고 인증 기록이 소모된다")
    void signup_withVerification_succeeds() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(authService.signup(request()).getEmail()).isEqualTo(EMAIL);

        verify(emailVerificationService).consumeVerified(EMAIL);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("중복 이메일 검사가 인증 확인보다 먼저 실행된다")
    void signup_duplicateEmail_doesNotConsumeVerification() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

        // 중복이면 인증 기록을 건드리지 않는다 — 남의 인증을 소모시키지 않기 위해서다.
        verify(emailVerificationService, never()).consumeVerified(anyString());
    }
}
