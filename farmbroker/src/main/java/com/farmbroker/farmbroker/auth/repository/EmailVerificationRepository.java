package com.farmbroker.farmbroker.auth.repository;

import com.farmbroker.farmbroker.auth.domain.EmailVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

// 이메일 1건당 행 1개(EmailVerification의 email unique)라 이메일로 찾는 조회 하나면 충분하다.
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmail(String email);

    // 시도 횟수 검사와 증가 사이에 다른 트랜잭션이 끼어들지 못하게 행을 잠그고 읽는다.
    // 잠금이 없으면 병렬 오답이 모두 같은 attemptCount를 읽어 제한을 통과한 뒤 서로를 덮어써
    // 5회 제한이 사실상 무력해진다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findWithLockByEmail(String email);
}
