import { ApiError, apiRequest, USE_MOCKS } from '@/api/client';
import { ENDPOINTS } from '@/api/endpoints';
import { getStoredUser } from '@/auth/session';
import { mockDelay } from '@/mocks/handlers';
import type {
  EmailVerificationConfirmInput,
  EmailVerificationSendInput,
  EmailVerificationSendResult,
  LoginInput,
  LoginResult,
  SignupInput,
  User,
  WebSocketTicketResult,
} from '@/types/api';

// 목 사용자는 공간을 등록해 본 소비자 — 여러 역할을 동시에 가진 상태를 기본값으로 둡니다.
const mockUser: User = {
  userId: 1,
  email: 'owner@example.com',
  nickname: '그린스페이스랩',
  roles: ['OWNER', 'CONSUMER'],
};

export async function login(input: LoginInput): Promise<LoginResult> {
  if (USE_MOCKS) {
    await mockDelay();
    return {
      user: { ...mockUser, email: input.email },
    };
  }

  const response = await apiRequest<LoginResult>(ENDPOINTS.auth.login, {
    method: 'POST',
    body: input,
  });
  return response.data;
}

// Stateless JWT 로그아웃은 서버에 현재 토큰을 확인시킨 뒤 클라이언트 세션을 지우는 흐름입니다.
export async function logout(): Promise<void> {
  if (USE_MOCKS) {
    await mockDelay();
    return;
  }

  await apiRequest<void>(ENDPOINTS.auth.logout, { method: 'POST' });
}

export async function getWebSocketTicket(): Promise<WebSocketTicketResult> {
  if (USE_MOCKS) {
    await mockDelay();
    return { ticket: 'mock-websocket-ticket', expiresInSeconds: 60 };
  }

  const response = await apiRequest<WebSocketTicketResult>(
    ENDPOINTS.auth.websocketTicket,
    { method: 'POST' },
  );
  return response.data;
}

// 목 환경에서 인증 성공 경로를 재현하기 위한 고정 인증번호입니다. 테스트가 이 값을 가져다 씁니다.
export const MOCK_EMAIL_VERIFICATION_CODE = '123456';

export async function sendEmailVerificationCode(
  input: EmailVerificationSendInput,
): Promise<EmailVerificationSendResult> {
  if (USE_MOCKS) {
    await mockDelay();
    return { expiresInSeconds: 300, resendAfterSeconds: 60 };
  }

  const response = await apiRequest<EmailVerificationSendResult>(
    ENDPOINTS.auth.sendEmailCode,
    { method: 'POST', body: input },
  );
  return response.data;
}

export async function verifyEmailCode(
  input: EmailVerificationConfirmInput,
): Promise<void> {
  if (USE_MOCKS) {
    await mockDelay();
    // 실패 경로도 테스트할 수 있도록 서버와 같은 메시지·errorCode로 던집니다.
    if (input.code !== MOCK_EMAIL_VERIFICATION_CODE) {
      throw new ApiError(
        '인증번호가 일치하지 않습니다.',
        400,
        'EMAIL_VERIFICATION_CODE_MISMATCH',
      );
    }
    return;
  }

  await apiRequest<void>(ENDPOINTS.auth.verifyEmailCode, {
    method: 'POST',
    body: input,
  });
}

export async function signup(input: SignupInput): Promise<User> {
  if (USE_MOCKS) {
    await mockDelay();
    return {
      userId: 2,
      email: input.email,
      nickname: input.nickname,
      roles: ['CONSUMER'],
    };
  }

  const response = await apiRequest<User>(ENDPOINTS.auth.signup, {
    method: 'POST',
    body: input,
  });
  return response.data;
}

export async function getCurrentUser(): Promise<User> {
  if (USE_MOCKS) {
    await mockDelay();
    // 목 환경에는 실제 쿠키가 없으므로 캐시된 세션을 현재 사용자로 간주한다.
    // 세션이 없으면 백엔드의 미인증 응답과 동일하게 401로 처리한다.
    const stored = getStoredUser();
    if (!stored) {
      throw new ApiError('인증이 필요합니다.', 401, 'UNAUTHORIZED');
    }
    return stored;
  }

  const response = await apiRequest<User>(ENDPOINTS.users.me);
  return response.data;
}
