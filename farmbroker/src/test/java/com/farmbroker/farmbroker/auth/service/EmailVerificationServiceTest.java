package com.farmbroker.farmbroker.auth.service;

import com.farmbroker.farmbroker.auth.domain.EmailVerification;
import com.farmbroker.farmbroker.auth.dto.EmailVerificationSendResponse;
import com.farmbroker.farmbroker.auth.mail.EmailVerificationProperties;
import com.farmbroker.farmbroker.auth.mail.VerificationMailSender;
import com.farmbroker.farmbroker.auth.repository.EmailVerificationRepository;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 인증번호 발송 · 확인의 도메인 규칙을 검증한다. DB와 SMTP 없이 돌도록 모두 목으로 대체한다.
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "farmer@example.com";

    @Mock
    private EmailVerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationMailSender mailSender;

    // record라 목으로 감싸지 않고 실제 값을 쓴다 — 정책 값 자체가 검증 대상이다.
    private final EmailVerificationProperties properties =
            new EmailVerificationProperties(300, 60, 5, 30);

    private EmailVerificationService service() {
        return new EmailVerificationService(verificationRepository, userRepository, mailSender, properties);
    }

    // 만료 · 쿨다운 경계를 만들기 위해 발급 시각을 직접 넣는다.
    private static EmailVerification record(String code, LocalDateTime sentAt, Duration ttl) {
        return EmailVerification.issue(EMAIL, code, sentAt, ttl);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 메일을 보내지 않고 DUPLICATE_EMAIL을 던진다")
    void sendCode_duplicateEmail_doesNotSendMail() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> service().sendCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

        verify(mailSender, never()).send(any(), any());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("재발송 쿨다운 안에 다시 요청하면 EMAIL_VERIFICATION_TOO_FREQUENT를 던진다")
    void sendCode_withinCooldown_throws() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        // 방금 보낸 기록 — 60초 쿨다운이 아직 남아 있다.
        given(verificationRepository.findByEmail(EMAIL))
                .willReturn(Optional.of(record("123456", LocalDateTime.now(), properties.ttl())));

        assertThatThrownBy(() -> service().sendCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);

        verify(mailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("쿨다운이 지났으면 새 행을 쌓지 않고 같은 행을 재발급한다")
    void sendCode_afterCooldown_reissues() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        EmailVerification existing =
                record("111111", LocalDateTime.now().minusMinutes(5), properties.ttl());
        given(verificationRepository.findByEmail(EMAIL)).willReturn(Optional.of(existing));

        EmailVerificationSendResponse response = service().sendCode(EMAIL);

        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.resendAfterSeconds()).isEqualTo(60);
        verify(verificationRepository, never()).save(any());
        assertThat(existing.getCode()).hasSize(6);
    }

    @Test
    @DisplayName("메일 발송이 실패하면 인증 정보를 저장하지 않는다")
    void sendCode_mailFailure_doesNotSave() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(verificationRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        willThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_FAILED))
                .given(mailSender).send(any(), any());

        assertThatThrownBy(() -> service().sendCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_SEND_FAILED);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("코드가 일치하면 인증 완료 시각을 기록한다")
    void verifyCode_match_marksVerified() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.of(existing));

        service().verifyCode(EMAIL, "123456");

        assertThat(existing.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("대소문자와 공백이 달라도 같은 인증 기록을 찾는다")
    void verifyCode_normalizesEmail() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.of(existing));

        service().verifyCode("  Farmer@Example.COM  ", "123456");

        assertThat(existing.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("코드가 틀리면 시도 횟수가 늘고 CODE_MISMATCH를 던진다")
    void verifyCode_mismatch_increasesAttempt() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().verifyCode(EMAIL, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);

        assertThat(existing.getAttemptCount()).isEqualTo(1);
        assertThat(existing.getVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("만료된 코드는 EMAIL_VERIFICATION_EXPIRED를 던진다")
    void verifyCode_expired_throws() {
        EmailVerification existing =
                record("123456", LocalDateTime.now().minusMinutes(10), properties.ttl());
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().verifyCode(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_EXPIRED);
    }

    @Test
    @DisplayName("발송 이력이 없으면 EMAIL_VERIFICATION_EXPIRED를 던진다")
    void verifyCode_noRecord_throwsExpired() {
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().verifyCode(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_EXPIRED);
    }

    @Test
    @DisplayName("시도 횟수를 초과하면 정답을 넣어도 ATTEMPT_EXCEEDED를 던진다")
    void verifyCode_attemptsExceeded_throws() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        ReflectionTestUtils.setField(existing, "attemptCount", 5);
        given(verificationRepository.findWithLockByEmail(EMAIL)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().verifyCode(EMAIL, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED);
    }

    @Test
    @DisplayName("인증을 마쳤으면 기록을 삭제해 재사용을 막는다")
    void consumeVerified_verified_deletesRecord() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        existing.markVerified(LocalDateTime.now());
        given(verificationRepository.findByEmail(EMAIL)).willReturn(Optional.of(existing));

        service().consumeVerified(EMAIL);

        verify(verificationRepository).delete(existing);
    }

    @Test
    @DisplayName("인증한 지 유효창을 넘겼으면 EMAIL_NOT_VERIFIED를 던진다")
    void consumeVerified_staleVerification_throws() {
        EmailVerification existing = record("123456", LocalDateTime.now(), properties.ttl());
        existing.markVerified(LocalDateTime.now().minusMinutes(31));
        given(verificationRepository.findByEmail(EMAIL)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().consumeVerified(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        verify(verificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("인증하지 않은 이메일이면 EMAIL_NOT_VERIFIED를 던진다")
    void consumeVerified_notVerified_throws() {
        given(verificationRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().consumeVerified(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);
    }
}
