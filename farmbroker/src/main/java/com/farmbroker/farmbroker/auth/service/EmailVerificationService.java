package com.farmbroker.farmbroker.auth.service;

import com.farmbroker.farmbroker.auth.domain.EmailVerification;
import com.farmbroker.farmbroker.auth.dto.EmailVerificationSendResponse;
import com.farmbroker.farmbroker.auth.mail.EmailVerificationProperties;
import com.farmbroker.farmbroker.auth.mail.VerificationMailSender;
import com.farmbroker.farmbroker.auth.repository.EmailVerificationRepository;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

// 회원가입 전 이메일 인증 로직.
// 인증번호를 발송하고(sendCode), 사용자가 입력한 번호를 확인하고(verifyCode),
// 회원가입 시점에 인증 여부를 다시 확인하며 기록을 소모한다(consumeVerified).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final VerificationMailSender mailSender;
    private final EmailVerificationProperties properties;

    private static final SecureRandom RANDOM = new SecureRandom();

    // 인증 테이블 안에서만 이메일을 정규화한다.
    // 대소문자가 다른 두 값이 서로 다른 행이 되면 "인증은 했는데 가입이 막히는" 버그가 난다.
    // 반대로 users 테이블은 기존 저장 방식을 그대로 두어야 기존 계정과 어긋나지 않는다.
    private static String normalize(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    // 메일 발송이 트랜잭션 안에 있어 SMTP 응답을 기다리는 1~3초 동안 DB 커넥션을 붙잡는다.
    // 동시 접속이 한 자릿수인 규모라 커넥션 풀 압박이 없고, 트랜잭션을 쪼개려면
    // 자기호출 프록시 문제를 피할 별도 빈이 필요해 단순한 쪽을 택했다.
    @Transactional
    public EmailVerificationSendResponse sendCode(String rawEmail) {
        String email = normalize(rawEmail);

        // 이미 가입된 주소라면 인증을 진행할 이유가 없다.
        // 회원가입 응답이 이미 DUPLICATE_EMAIL을 그대로 돌려주고 있어 새로 새는 정보는 없고,
        // 여기서 막지 않으면 사용자가 메일을 기다려 인증까지 마친 뒤에야 중복을 알게 된다.
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerification record = verificationRepository.findByEmail(email).orElse(null);

        if (record != null && record.resendBlocked(now, properties.resendCooldown())) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
        }

        String code = generateCode();

        // 저장보다 발송이 먼저다 — 발송이 실패하면 트랜잭션이 롤백되어 쿨다운이 시작되지 않는다.
        // (메일이 오지도 않았는데 60초 동안 재발송이 막히면 사용자가 빠져나갈 길이 없다.)
        mailSender.send(email, code);

        if (record == null) {
            verificationRepository.save(EmailVerification.issue(email, code, now, properties.ttl()));
        } else {
            record.reissue(code, now, properties.ttl());
        }

        return new EmailVerificationSendResponse(
                properties.ttlSeconds(), properties.resendCooldownSeconds());
    }

    // noRollbackFor가 없으면 시도 횟수 제한이 조용히 무력화된다.
    // increaseAttempt() 뒤에 BusinessException을 던지면 같은 트랜잭션이 롤백되면서
    // 늘려 둔 카운트까지 함께 사라져 몇 번을 틀려도 계속 시도할 수 있게 된다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        // 발송 이력이 없는 경우도 만료와 같은 코드로 안내한다 — 어느 쪽이든 할 일은 재발송이다.
        // 행을 잠그고 읽어 조회 · 검사 · 증가를 직렬화한다. 잠그지 않으면 병렬 요청이
        // 같은 attemptCount를 읽고 검사를 통과해 시도 제한을 우회할 수 있다.
        EmailVerification record = verificationRepository.findWithLockByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED));

        if (record.expired(now)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (record.attemptsExceeded(properties.maxAttempts())) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED);
        }
        if (!record.matches(code)) {
            record.increaseAttempt();
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        record.markVerified(now);
    }

    // 회원가입이 부르는 유일한 진입점 — 인증 확인과 기록 소모를 한 번에 한다.
    // 호출자(AuthService.signup)의 트랜잭션에 참여하므로, 이후 저장이 실패하면 삭제도 함께 롤백된다.
    @Transactional
    public void consumeVerified(String rawEmail) {
        String email = normalize(rawEmail);
        LocalDateTime now = LocalDateTime.now();

        EmailVerification record = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));

        if (!record.verifiedWithin(now, properties.verifiedWindow())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 같은 인증으로 두 번 가입하는 경로를 막는다. 재가입하려면 다시 인증해야 한다.
        verificationRepository.delete(record);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
