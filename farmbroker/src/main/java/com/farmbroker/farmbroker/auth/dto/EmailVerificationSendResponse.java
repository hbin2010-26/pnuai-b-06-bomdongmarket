package com.farmbroker.farmbroker.auth.dto;

// 발송 성공 응답. 유효 시간과 재발송 가능 시각을 함께 내려 프론트 타이머가
// 서버 정책(설정으로 바뀔 수 있다)을 하드코딩하지 않게 한다.
// 필드가 둘뿐이라 record로 둔다 (AuthService.LoginResult 선례).
public record EmailVerificationSendResponse(int expiresInSeconds, int resendAfterSeconds) {
}
