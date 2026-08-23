package com.farmbroker.farmbroker.auth.controller;

import com.farmbroker.farmbroker.auth.dto.EmailVerificationSendResponse;
import com.farmbroker.farmbroker.auth.service.AuthService;
import com.farmbroker.farmbroker.auth.service.EmailVerificationService;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.security.AuthCookieProvider;
import com.farmbroker.farmbroker.security.JwtAuthenticationFilter;
import com.farmbroker.farmbroker.security.JwtTokenProvider;
import com.farmbroker.farmbroker.security.SecurityConfig;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 이메일 인증 엔드포인트의 공개 계약을 검증한다.
// SecurityConfig를 실제로 로드하므로, permitAll 목록에 두 경로를 넣는 것을 빠뜨리면 여기서 401로 잡힌다.
// (회원가입 전 단계라 토큰이 있을 수 없어 인증이 걸리면 기능 자체가 동작하지 않는다.)
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, AuthCookieProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-email-verification-0123456789",
        "jwt.expiration=86400000"
})
class AuthControllerEmailVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("인증 없이 인증번호 발송을 호출하면 200과 유효시간을 반환한다")
    void sendCode_withoutAuthentication_returns200() throws Exception {
        given(emailVerificationService.sendCode(anyString()))
                .willReturn(new EmailVerificationSendResponse(300, 60));

        mockMvc.perform(post("/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60));
    }

    @Test
    @DisplayName("인증 없이 인증번호 확인을 호출하면 200을 반환한다")
    void verifyCode_withoutAuthentication_returns200() throws Exception {
        mockMvc.perform(post("/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("이메일 인증이 완료되었습니다."));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 VALIDATION_ERROR를 반환한다")
    void sendCode_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("인증번호가 6자리 숫자가 아니면 400을 반환한다")
    void verifyCode_malformedCode_returns400() throws Exception {
        mockMvc.perform(post("/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.com\",\"code\":\"12ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("이미 가입된 이메일로 발송하면 409 DUPLICATE_EMAIL을 반환한다")
    void sendCode_duplicateEmail_returns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL))
                .given(emailVerificationService).sendCode(anyString());

        mockMvc.perform(post("/auth/email/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("인증번호가 틀리면 400 EMAIL_VERIFICATION_CODE_MISMATCH를 반환한다")
    void verifyCode_mismatch_returns400() throws Exception {
        willThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH))
                .given(emailVerificationService).verifyCode(anyString(), anyString());

        mockMvc.perform(post("/auth/email/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_CODE_MISMATCH"));
    }
}
