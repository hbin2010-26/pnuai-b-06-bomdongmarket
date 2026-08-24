import { ArrowRight, Lock, UserRound } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { PageHeader } from '@/components/common/PageHeader';
import { PageContainer } from '@/components/layout/PageContainer';
import { APP_INFO } from '@/constants/appInfo';
import { ROUTES } from '@/constants/routes';
import { EmailVerificationField } from '@/pages/auth/components/EmailVerificationField';
import { useEmailVerification } from '@/pages/auth/hooks/useEmailVerification';
import { useSignupForm } from '@/pages/auth/hooks/useSignupForm';
import { signup } from '@/services/authService';

export function SignupPage() {
  const navigate = useNavigate();
  const verification = useEmailVerification();
  const {
    values,
    errors,
    hasErrors,
    isSubmitting,
    submitError,
    handleTextChange,
    handleBlur,
    handleSubmit,
  } = useSignupForm(
    async (input) => {
      await signup(input);
      navigate(ROUTES.login, { replace: true, state: { signupCompleted: true } });
    },
    // 인증 대상 이메일이 폼의 현재 이메일과 같을 때만 인증된 것으로 봅니다.
    { isEmailVerified: verification.isVerifiedFor },
  );

  return (
    <PageContainer className="pb-28 pt-10 sm:pt-14 lg:py-16" narrow>
      <div className="mx-auto w-full max-w-2xl">
        <PageHeader
          align="center"
          description="도심 스마트팜 여정을 시작하세요."
          eyebrow={`Join ${APP_INFO.name}`}
          title={`${APP_INFO.name} 회원가입`}
        />

        <Card className="mt-6 p-6 shadow-lift sm:p-8">
          <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
            {submitError ? (
              <div
                className="rounded-app border border-red-200 bg-red-50 p-3 text-sm font-semibold text-red-700"
                role="alert"
              >
                {submitError}
              </div>
            ) : null}

            <Input
              autoComplete="nickname"
              errorMessage={errors.nickname}
              icon={<UserRound className="h-4 w-4" aria-hidden />}
              label="이름 또는 닉네임"
              maxLength={30}
              name="nickname"
              onBlur={handleBlur}
              onChange={handleTextChange}
              placeholder="표시할 이름을 입력해 주세요"
              required
              value={values.nickname}
            />

            <EmailVerificationField
              email={values.email}
              formError={errors.email}
              onEmailBlur={handleBlur}
              onEmailChange={handleTextChange}
              verification={verification}
            />

            <div className="grid gap-5 sm:grid-cols-2">
              <Input
                autoComplete="new-password"
                errorMessage={errors.password}
                icon={<Lock className="h-4 w-4" aria-hidden />}
                label="비밀번호"
                minLength={8}
                name="password"
                onBlur={handleBlur}
                onChange={handleTextChange}
                placeholder="8자 이상 입력해 주세요"
                required
                type="password"
                value={values.password}
              />
              <Input
                autoComplete="new-password"
                errorMessage={errors.passwordConfirm}
                icon={<Lock className="h-4 w-4" aria-hidden />}
                label="비밀번호 확인"
                minLength={8}
                name="passwordConfirm"
                onBlur={handleBlur}
                onChange={handleTextChange}
                placeholder="비밀번호를 다시 입력해 주세요"
                required
                type="password"
                value={values.passwordConfirm}
              />
            </div>

            <p className="rounded-app border border-leaf-100 bg-leaf-50 p-3 text-xs leading-5 text-slate-600">
              사용자 유형을 미리 고르지 않아도 됩니다. 유휴 공간을 등록하면 공간 제공자,
              매칭이 성사되면 도심 농부 역할이 더해지고 여러 역할을 함께 가질 수 있습니다.
            </p>

            <Button
              className="mt-1 w-full"
              disabled={isSubmitting || hasErrors}
              type="submit"
            >
              {isSubmitting ? '가입 중...' : '회원가입'}
              <ArrowRight className="h-5 w-5" aria-hidden />
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-slate-600">
            이미 계정이 있으신가요?{' '}
            <Link className="font-bold text-leaf-700" to={ROUTES.login}>
              로그인
            </Link>
          </p>
        </Card>
      </div>
    </PageContainer>
  );
}
