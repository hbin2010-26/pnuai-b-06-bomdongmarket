package com.farmbroker.farmbroker.auth.repository;

import com.farmbroker.farmbroker.auth.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// 이메일 1건당 행 1개(EmailVerification의 email unique)라 이메일로 찾는 조회 하나면 충분하다.
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmail(String email);
}
