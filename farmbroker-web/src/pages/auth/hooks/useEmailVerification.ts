import { useCallback, useState } from 'react';

import { ApiError } from '@/api/client';
import { useCountdown } from '@/hooks/useCountdown';
import { sendEmailVerificationCode, verifyEmailCode } from '@/services/authService';

type VerificationStatus = 'idle' | 'sending' | 'sent' | 'verifying' | 'verified';

// 인증번호 칸에 붙일 오류. 나머지는 이메일 칸이나 상단 블록으로 보냅니다.
const CODE_FIELD_ERROR_CODES = new Set([
  'EMAIL_VERIFICATION_CODE_MISMATCH',
  'EMAIL_VERIFICATION_EXPIRED',
  'EMAIL_VERIFICATION_ATTEMPT_EXCEEDED',
]);

function normalizeEmail(email: string) {
  return email.trim().toLowerCase();
}

// 회원가입 화면의 이메일 인증 단계를 관리합니다.
//
// 이메일 값을 인자로 받지 않고 호출 시점에 넘겨받습니다. 인자로 받으면 이 훅이
// useSignupForm(이메일 값의 주인)보다 뒤에 와야 하는데, useSignupForm은 인증 여부를 필요로 해
// 선언 순서가 순환합니다. isVerifiedFor(email)로 노출하면 그 제약이 사라집니다.
export function useEmailVerification() {
  // 인증번호를 보낸 대상. 이메일을 고치면 이 값과 어긋나 인증이 자동으로 풀립니다.
  const [requestedEmail, setRequestedEmail] = useState('');
  const [status, setStatus] = useState<VerificationStatus>('idle');
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [blockError, setBlockError] = useState<string | null>(null);

  const expiry = useCountdown();
  const resend = useCountdown();

  // 이메일이 바뀌면 이전 인증은 무효입니다.
  // 상태를 지우는 effect 대신 매번 대상을 비교해 되돌림이 누락될 여지를 없앱니다.
  const matchesRequested = useCallback(
    (email: string) => {
      const normalized = normalizeEmail(email);
      return normalized.length > 0 && normalized === requestedEmail;
    },
    [requestedEmail],
  );

  const statusFor = useCallback(
    (email: string): VerificationStatus => (matchesRequested(email) ? status : 'idle'),
    [matchesRequested, status],
  );

  const isVerifiedFor = useCallback(
    (email: string) => statusFor(email) === 'verified',
    [statusFor],
  );

  function clearErrors() {
    setCodeError(null);
    setEmailError(null);
    setBlockError(null);
  }

  // 서버 메시지를 그대로 보여 줍니다 — 문구의 단일 출처를 서버에 둡니다.
  function applyError(caught: unknown, fallback: string) {
    const message = caught instanceof Error ? caught.message : fallback;
    const errorCode = caught instanceof ApiError ? caught.errorCode : undefined;

    if (errorCode === 'DUPLICATE_EMAIL') {
      setEmailError(message);
    } else if (errorCode && CODE_FIELD_ERROR_CODES.has(errorCode)) {
      setCodeError(message);
    } else {
      setBlockError(message);
    }
  }

  async function sendCode(email: string) {
    const target = normalizeEmail(email);
    clearErrors();
    setCode('');
    setRequestedEmail(target);
    setStatus('sending');

    try {
      const result = await sendEmailVerificationCode({ email: target });
      setStatus('sent');
      expiry.start(result.expiresInSeconds);
      resend.start(result.resendAfterSeconds);
    } catch (caught) {
      // 발송에 실패했으면 인증번호 칸을 띄우지 않습니다.
      setStatus('idle');
      applyError(caught, '인증번호를 보내지 못했습니다.');
    }
  }

  async function confirmCode(email: string) {
    clearErrors();
    setStatus('verifying');

    try {
      await verifyEmailCode({ email: normalizeEmail(email), code });
      setStatus('verified');
      expiry.stop();
      resend.stop();
    } catch (caught) {
      setStatus('sent');
      applyError(caught, '인증번호를 확인하지 못했습니다.');
    }
  }

  return {
    code,
    setCode,
    codeError,
    emailError,
    blockError,
    statusFor,
    isVerifiedFor,
    expiresIn: expiry.remaining,
    resendIn: resend.remaining,
    sendCode,
    confirmCode,
  };
}

export type EmailVerification = ReturnType<typeof useEmailVerification>;
