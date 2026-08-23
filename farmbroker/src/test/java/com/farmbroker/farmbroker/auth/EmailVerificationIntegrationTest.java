package com.farmbroker.farmbroker.auth;

import com.farmbroker.farmbroker.auth.dto.SignupRequest;
import com.farmbroker.farmbroker.auth.mail.VerificationMailSender;
import com.farmbroker.farmbroker.auth.repository.EmailVerificationRepository;
import com.farmbroker.farmbroker.auth.service.AuthService;
import com.farmbroker.farmbroker.auth.service.EmailVerificationService;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

// 목 없이 실제 트랜잭션에 대고 도는 검증.
//
// 단위 테스트로는 확인할 수 없는 것만 여기서 본다 —
// 인증번호가 틀렸을 때 늘린 시도 횟수가 예외와 함께 롤백되지 않고 실제로 남는지,
// 그리고 발송 → 확인 → 가입이 한 흐름으로 이어지는지.
//
// 시도 횟수는 EmailVerificationService.verifyCode의 noRollbackFor에 전적으로 달려 있는데,
// 그 어노테이션을 빼면 제한이 조용히 사라진다. 목으로는 트랜잭션이 없어 잡히지 않는다.
//
// [한계] H2(MySQL 모드)라 스키마 의미까지 MySQL과 같지는 않다.
@SpringBootTest(properties = {
        // LOCK_TIMEOUT을 올린다 — 동시성 테스트가 한 행에 20개 트랜잭션을 몰아
        // H2 기본 타임아웃(1초)에 걸리면 잠금이 동작하는데도 테스트가 깨진다.
        "spring.datasource.url=jdbc:h2:mem:emailverification;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=01234567890123456789012345678901",
        "file.upload-dir=${java.io.tmpdir}/farmbroker-test-uploads",
        "file.chat-upload-dir=${java.io.tmpdir}/farmbroker-test-chat-uploads"
})
@DisplayName("이메일 인증 통합")
class EmailVerificationIntegrationTest {

    private static final String EMAIL = "integration@example.com";

    @Autowired EmailVerificationService emailVerificationService;
    @Autowired EmailVerificationRepository verificationRepository;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;

    // SMTP를 태우지 않으면서도 서버가 실제로 발급한 인증번호를 잡아내기 위해 발송기만 목으로 둔다.
    @MockitoBean VerificationMailSender mailSender;

    @BeforeEach
    void clear() {
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String sendAndCaptureCode() {
        emailVerificationService.sendCode(EMAIL);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(org.mockito.ArgumentMatchers.eq(EMAIL), code.capture());
        return code.getValue();
    }

    private static SignupRequest signupRequest(String email) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "nickname", "도시농부");
        return request;
    }

    @Test
    @DisplayName("발송한 인증번호로 확인하면 가입까지 이어지고 인증 기록이 사라진다")
    void sendVerifyThenSignup() {
        String code = sendAndCaptureCode();
        assertThat(code).hasSize(6).containsOnlyDigits();

        emailVerificationService.verifyCode(EMAIL, code);
        assertThat(authService.signup(signupRequest(EMAIL)).getEmail()).isEqualTo(EMAIL);

        // 같은 인증으로 두 번 가입할 수 없도록 기록이 소모된다.
        assertThat(verificationRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("인증하지 않은 이메일은 가입이 막힌다")
    void signupWithoutVerificationIsBlocked() {
        assertThatThrownBy(() -> authService.signup(signupRequest(EMAIL)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);

        assertThat(userRepository.existsByEmail(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("틀린 인증번호로 시도한 횟수는 예외와 함께 롤백되지 않고 누적된다")
    void failedAttemptsAccumulateAcrossTransactions() {
        sendAndCaptureCode();

        for (int i = 1; i <= 5; i++) {
            int attempt = i;
            assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, "000000"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);

            // 커밋된 값을 다시 읽는다 — 롤백되었다면 여기서 0에 머문다.
            assertThat(verificationRepository.findByEmail(EMAIL))
                    .get()
                    .extracting("attemptCount")
                    .as("%d번째 시도 후 누적 횟수", attempt)
                    .isEqualTo(attempt);
        }

        // 5회를 채우면 이후에는 정답을 넣어도 막힌다.
        assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED);
    }

    @Test
    @DisplayName("동시에 틀린 인증번호를 넣어도 시도 제한을 넘지 못한다")
    void concurrentWrongAttemptsCannotBypassLimit() throws Exception {
        sendAndCaptureCode();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger mismatched = new AtomicInteger();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    // 순차 실행으로 흘러가면 잠금이 없어도 통과하므로, 모두 모인 뒤 동시에 출발한다.
                    ready.countDown();
                    fire.await();
                    try {
                        emailVerificationService.verifyCode(EMAIL, "000000");
                    } catch (BusinessException caught) {
                        if (caught.getErrorCode() == ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH) {
                            mismatched.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 잠금 없이 읽으면 20건이 모두 같은 attemptCount를 보고 제한을 통과한다.
        assertThat(mismatched.get()).as("제한을 통과한 오답 횟수").isEqualTo(5);
        assertThat(verificationRepository.findByEmail(EMAIL))
                .get()
                .extracting("attemptCount")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("재발송 쿨다운 안에는 다시 보내지 않는다")
    void resendWithinCooldownIsBlocked() {
        sendAndCaptureCode();

        assertThatThrownBy(() -> emailVerificationService.sendCode(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
    }
}
