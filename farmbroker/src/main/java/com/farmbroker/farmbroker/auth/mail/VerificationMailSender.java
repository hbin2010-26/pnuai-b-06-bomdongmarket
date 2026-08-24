package com.farmbroker.farmbroker.auth.mail;

import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 인증번호 메일 발송. 본문은 평문(SimpleMailMessage)으로 보낸다 —
// 6자리 숫자 하나를 전달하는 데 HTML은 MimeMessageHelper와 인코딩 처리를 더할 뿐 얻는 게 없고,
// 손으로 짠 HTML 메일은 스팸 판정을 더 잘 받는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailSender {

    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    // Gmail은 인증한 계정과 다른 From을 허용하지 않으므로 계정 주소를 그대로 발신자로 쓴다.
    @Value("${spring.mail.username:}")
    private String from;

    public void send(String to, String code) {
        // SMTP 계정이 없는 개발 환경에서도 가입 흐름 전체를 돌려볼 수 있도록 콘솔 출력으로 대체한다.
        // 계정이 설정돼 있으면 이 분기를 타지 않으므로 운영 로그에 인증번호가 남지 않는다.
        if (!StringUtils.hasText(from)) {
            log.warn("[DEV] MAIL_USERNAME이 비어 있어 메일 대신 콘솔에 출력한다. to={} code={}", to, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("[FarmBroker] 이메일 인증번호를 확인해 주세요");
        message.setText("""
                FarmBroker 회원가입을 위한 인증번호입니다.

                인증번호: %s

                인증번호는 발송 시각으로부터 %d분간 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code, Math.max(1, properties.ttlSeconds() / 60)));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 실패 원인(앱 비밀번호 오류 · 네트워크)은 서버 로그로만 남기고,
            // 사용자에게는 재시도 안내만 준다. 인증번호는 로그에 찍지 않는다.
            log.error("인증 메일 발송에 실패했다. to={}", to, e);
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_FAILED);
        }
    }
}
