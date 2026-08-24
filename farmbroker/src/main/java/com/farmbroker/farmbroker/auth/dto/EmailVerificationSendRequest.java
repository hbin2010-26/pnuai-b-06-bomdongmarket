package com.farmbroker.farmbroker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 인증번호 발송 요청. 회원가입 전 단계라 인증 없이 호출된다.
@Getter
@NoArgsConstructor
public class EmailVerificationSendRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
