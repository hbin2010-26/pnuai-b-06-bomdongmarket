package com.farmbroker.farmbroker.auth.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 회원가입 이메일 인증 정책. 발표 중에 값을 바꿔야 할 수 있어 코드에 박지 않고 설정으로 뺀다.
// @ConfigurationPropertiesScan이 이미 FarmbrokerApplication에 있어 별도 등록 코드는 필요 없다.
// KamisProperties와 같은 이유로 생성자는 하나만 둔다 — 두 개면 Spring이 바인딩 생성자를 못 고른다.
@ConfigurationProperties(prefix = "auth.email-verification")
public record EmailVerificationProperties(
        // 인증번호 유효 시간.
        int ttlSeconds,
        // 재발송 쿨다운. Gmail 앱 비밀번호의 일일 발송 한도를 연타로 태우지 않게 한다.
        int resendCooldownSeconds,
        // 인증번호 입력 시도 제한. 6자리(10^6)에 5회면 우연히 맞을 확률은 무시할 수준이다.
        int maxAttempts,
        // 인증을 마치고 이 시간 안에 가입해야 한다.
        int verifiedWindowMinutes
) {
    public EmailVerificationProperties {
        if (ttlSeconds <= 0) ttlSeconds = 300;
        if (resendCooldownSeconds <= 0) resendCooldownSeconds = 60;
        if (maxAttempts <= 0) maxAttempts = 5;
        if (verifiedWindowMinutes <= 0) verifiedWindowMinutes = 30;
    }

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }

    public Duration resendCooldown() {
        return Duration.ofSeconds(resendCooldownSeconds);
    }

    public Duration verifiedWindow() {
        return Duration.ofMinutes(verifiedWindowMinutes);
    }
}
