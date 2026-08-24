package com.farmbroker.farmbroker.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// 회원가입 전 이메일 인증번호를 담는 JPA 엔티티.
//
// 이메일 1건당 행 1개만 둔다(email unique). 재발송은 새 행을 쌓지 않고 같은 행을 reissue()로 덮어쓴다.
// 덕분에 "가장 최근 행이 무엇인가"를 판별하는 로직이 필요 없고(findByEmail 단일 조회),
// 시도할 때마다 행이 늘지 않으므로 만료 행을 지우는 @Scheduled 배치도 두지 않는다.
// 가입에 성공하면 그 행은 삭제되므로 남는 것은 "가입까지 가지 않은 서로 다른 이메일 수"뿐이다.
//
// 인증번호는 해싱하지 않고 평문으로 둔다. 6자리 숫자는 약 20비트라 DB가 유출된 상황에서
// BCrypt 해시를 오프라인으로 뚫는 데 10^6 시도면 충분해 해싱이 실질적인 안전을 사 주지 못한다.
// 게다가 행의 수명은 5분이고 가입 시 삭제된다. AGENT.md의 "평문 저장 금지"는 비밀번호 규칙이며
// 일회용 인증번호는 비밀번호가 아니다.
//
// 시각은 전부 인자로 받는다(엔티티 안에서 LocalDateTime.now()를 부르지 않는다).
// 그래야 서비스 단위 테스트가 시계를 고정한 채 만료 · 쿨다운 경계를 검증할 수 있다.
@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 재발송 쿨다운의 기준 시각이자 발급 시각. 이 값이 곧 생성 시각이라 @CreatedDate를 따로 두지 않는다.
    @Column(nullable = false)
    private LocalDateTime lastSentAt;

    @Column(nullable = false)
    private int attemptCount;

    // null이면 아직 인증 전. 회원가입 재확인이 "언제 인증했는가"를 봐야 하므로 boolean이 아니라 시각이다.
    @Column
    private LocalDateTime verifiedAt;

    @Builder
    private EmailVerification(String email, String code, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        this.email = email;
        this.code = code;
        this.expiresAt = expiresAt;
        this.lastSentAt = lastSentAt;
        this.attemptCount = 0;
    }

    public static EmailVerification issue(String email, String code, LocalDateTime now, Duration ttl) {
        return EmailVerification.builder()
                .email(email)
                .code(code)
                .expiresAt(now.plus(ttl))
                .lastSentAt(now)
                .build();
    }

    // 재발송 — 이전 인증 결과와 시도 횟수를 모두 초기화한다.
    // 인증을 마친 뒤 재발송하면 다시 인증해야 하는데, 그게 맞는 동작이다.
    public void reissue(String code, LocalDateTime now, Duration ttl) {
        this.code = code;
        this.expiresAt = now.plus(ttl);
        this.lastSentAt = now;
        this.attemptCount = 0;
        this.verifiedAt = null;
    }

    public boolean resendBlocked(LocalDateTime now, Duration cooldown) {
        return now.isBefore(lastSentAt.plus(cooldown));
    }

    public boolean expired(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    public boolean attemptsExceeded(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public boolean matches(String input) {
        return code.equals(input);
    }

    public void markVerified(LocalDateTime now) {
        this.verifiedAt = now;
    }

    // 인증을 마치고 일정 시간 안에 가입해야 한다 — 몇 달 전 인증으로 가입되는 경로를 막는다.
    public boolean verifiedWithin(LocalDateTime now, Duration window) {
        return verifiedAt != null && verifiedAt.isAfter(now.minus(window));
    }
}
