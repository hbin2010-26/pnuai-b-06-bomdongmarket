package com.farmbroker.farmbroker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 인증번호 확인 요청. 어떤 이메일에 대한 인증인지 함께 받아야 서버가 대상 행을 찾을 수 있다.
@Getter
@NoArgsConstructor
public class EmailVerificationConfirmRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "인증번호는 필수입니다.")
    @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다.")
    private String code;
}
