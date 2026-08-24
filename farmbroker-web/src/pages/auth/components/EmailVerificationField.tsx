import { CheckCircle2, Mail, ShieldCheck } from 'lucide-react';
import type { ChangeEvent, FocusEvent } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { formatRemaining } from '@/hooks/useCountdown';
import type { EmailVerification } from '@/pages/auth/hooks/useEmailVerification';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface EmailVerificationFieldProps {
  email: string;
  // 상위 폼(useSignupForm)이 붙인 형식 오류입니다. 인증 과정에서 나온 오류와는 출처가 다릅니다.
  formError?: string;
  onEmailChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onEmailBlur: (event: FocusEvent<HTMLInputElement>) => void;
  verification: EmailVerification;
}

// 이메일 소유를 확인하는 칸입니다. 실재하지 않는 주소로 가입되지 않도록
// 인증번호를 받아 입력해야 회원가입이 통과합니다.
export function EmailVerificationField({
  email,
  formError,
  onEmailChange,
  onEmailBlur,
  verification,
}: EmailVerificationFieldProps) {
  const status = verification.statusFor(email);
  const isVerified = status === 'verified';
  const isSending = status === 'sending';
  const isVerifying = status === 'verifying';
  const isCodeVisible = status !== 'idle' && !isVerified;
  const resendIn = verification.resendInFor(email);

  const canSend =
    EMAIL_PATTERN.test(email.trim()) && !isSending && !isVerifying && resendIn === 0;
  const isExpired = status === 'sent' && verification.expiresIn === 0;
  const sendLabel = status === 'idle' ? '인증코드 발송' : '재발송';

  // 라이브 리전이 겹치면 스크린리더가 같은 내용을 두 번 읽습니다 — 한 번에 하나만 렌더합니다.
  const statusMessage = isVerified
    ? '이메일 인증이 완료되었습니다.'
    : isSending
      ? '인증번호를 보내는 중입니다.'
      : isVerifying
        ? '인증번호를 확인하는 중입니다.'
        : status === 'sent' && !isExpired
          ? `인증번호를 보냈습니다. 메일함(스팸함 포함)을 확인해 주세요. 남은 시간 ${formatRemaining(verification.expiresIn)}`
          : null;

  return (
    <div className="grid gap-4">
      {verification.blockError ? (
        <div
          className="rounded-app border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700"
          role="alert"
        >
          {verification.blockError}
        </div>
      ) : null}

      {/* Input의 라벨 높이(text-sm 20px + mb-2 8px)만큼 버튼을 내려 입력칸과 윗변을 맞춥니다.
          AddressField와 같은 구성입니다. */}
      <div className="flex items-start gap-3">
        <div className="flex-1">
          <Input
            autoComplete="email"
            errorMessage={formError ?? verification.emailError ?? undefined}
            helperText={
              status === 'idle' ? '가입을 완료하려면 이메일 인증이 필요합니다.' : undefined
            }
            icon={<Mail className="h-4 w-4" aria-hidden />}
            label="이메일"
            name="email"
            onBlur={onEmailBlur}
            onChange={onEmailChange}
            placeholder="email@example.com"
            required
            type="email"
            value={email}
          />
        </div>
        {isVerified ? null : (
          <Button
            className="mt-7 shrink-0"
            disabled={!canSend}
            onClick={() => void verification.sendCode(email)}
            type="button"
            variant="outline"
          >
            {isSending
              ? '발송 중...'
              : resendIn > 0
                ? `재발송 (${resendIn}초 후)`
                : sendLabel}
          </Button>
        )}
      </div>

      {isCodeVisible ? (
        <div className="flex items-start gap-3">
          <div className="flex-1">
            <Input
              autoComplete="one-time-code"
              errorMessage={
                verification.codeError ??
                (isExpired ? '인증번호가 만료되었습니다. 다시 발송해 주세요.' : undefined)
              }
              icon={<ShieldCheck className="h-4 w-4" aria-hidden />}
              // 숫자 전용이라 모바일에서 숫자 키패드가 뜹니다.
              inputMode="numeric"
              label="인증번호"
              maxLength={6}
              name="emailVerificationCode"
              onChange={(event) => verification.setCode(event.target.value)}
              placeholder="6자리 숫자"
              value={verification.code}
            />
          </div>
          <Button
            className="mt-7 shrink-0"
            disabled={verification.code.length !== 6 || isVerifying || isExpired}
            onClick={() => void verification.confirmCode(email)}
            type="button"
            variant="outline"
          >
            {isVerifying ? '확인 중...' : '인증 확인'}
          </Button>
        </div>
      ) : null}

      {statusMessage ? (
        <p
          className={
            isVerified
              ? 'flex items-center gap-1.5 text-xs font-semibold text-leaf-700'
              : 'text-xs font-medium text-content-subtle'
          }
          role="status"
        >
          {isVerified ? <CheckCircle2 className="h-4 w-4" aria-hidden /> : null}
          {statusMessage}
        </p>
      ) : null}
    </div>
  );
}
