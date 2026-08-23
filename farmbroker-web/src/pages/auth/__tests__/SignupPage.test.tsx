import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { LoginPage } from '@/pages/auth/LoginPage';
import { SignupPage } from '@/pages/auth/SignupPage';
import { MOCK_EMAIL_VERIFICATION_CODE } from '@/services/authService';
import { renderWithProviders } from '@/test/renderWithProviders';

// 발송·확인이 각각 목 지연을 거치므로, 워커가 몰리는 전체 실행에서 기본 1초는 빠듯합니다.
const VERIFY_TIMEOUT = { timeout: 3000 };

// 회원가입은 이메일 인증을 마쳐야 통과하므로, 가입 흐름 테스트는 이 단계를 먼저 지나야 합니다.
async function verifyEmail(user: ReturnType<typeof userEvent.setup>, email: string) {
  await user.type(screen.getByLabelText('이메일'), email);
  await user.click(screen.getByRole('button', { name: '인증코드 발송' }));

  await user.type(
    await screen.findByLabelText('인증번호', undefined, VERIFY_TIMEOUT),
    MOCK_EMAIL_VERIFICATION_CODE,
  );
  await user.click(screen.getByRole('button', { name: '인증 확인' }));
  await screen.findByText('이메일 인증이 완료되었습니다.', undefined, VERIFY_TIMEOUT);
}

describe('SignupPage', () => {
  it('필수 입력값을 검증한다', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SignupPage />);

    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(screen.getByText('이름 또는 닉네임을 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('이메일을 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('비밀번호 확인을 입력해 주세요.')).toBeInTheDocument();
  });

  it('사용자 유형을 고르는 단계가 없다', () => {
    renderWithProviders(<SignupPage />);

    expect(screen.queryAllByRole('radio')).toHaveLength(0);
  });

  it('일치하지 않는 비밀번호를 안내한다', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SignupPage />);

    await user.type(screen.getByLabelText('비밀번호'), '12345678');
    await user.type(screen.getByLabelText('비밀번호 확인'), '87654321');
    await user.tab();

    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument();
  });

  it('회원가입을 완료하면 로그인 페이지로 이동한다', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route element={<SignupPage />} path="/signup" />
        <Route element={<LoginPage />} path="/login" />
      </Routes>,
      { route: '/signup' },
    );

    await user.type(screen.getByLabelText('이름 또는 닉네임'), '도시농부');
    await user.type(screen.getByLabelText('비밀번호'), '12345678');
    await user.type(screen.getByLabelText('비밀번호 확인'), '12345678');
    await verifyEmail(user, 'farmer@example.com');
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(
      await screen.findByRole('heading', { name: 'FarmBroker 로그인' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('회원가입이 완료되었습니다. 새 계정으로 로그인해 주세요.'),
    ).toBeInTheDocument();
  });

  it('이메일 인증 없이 회원가입을 시도하면 인증을 요구한다', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route element={<SignupPage />} path="/signup" />
        <Route element={<LoginPage />} path="/login" />
      </Routes>,
      { route: '/signup' },
    );

    await user.type(screen.getByLabelText('이름 또는 닉네임'), '도시농부');
    await user.type(screen.getByLabelText('이메일'), 'farmer@example.com');
    await user.type(screen.getByLabelText('비밀번호'), '12345678');
    await user.type(screen.getByLabelText('비밀번호 확인'), '12345678');
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(
      await screen.findByText('이메일 인증을 완료해 주세요.'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'FarmBroker 로그인' }),
    ).not.toBeInTheDocument();
  });

  it('인증코드를 발송하면 인증번호 칸이 나타난다', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SignupPage />);

    expect(screen.queryByLabelText('인증번호')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('이메일'), 'farmer@example.com');
    await user.click(screen.getByRole('button', { name: '인증코드 발송' }));

    expect(await screen.findByLabelText('인증번호')).toBeInTheDocument();
  });

  it('잘못된 인증번호를 입력하면 오류를 안내한다', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SignupPage />);

    await user.type(screen.getByLabelText('이메일'), 'farmer@example.com');
    await user.click(screen.getByRole('button', { name: '인증코드 발송' }));
    await user.type(await screen.findByLabelText('인증번호'), '000000');
    await user.click(screen.getByRole('button', { name: '인증 확인' }));

    expect(
      await screen.findByText('인증번호가 일치하지 않습니다.'),
    ).toBeInTheDocument();
  });

  it('이메일을 수정하면 인증 상태가 풀린다', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SignupPage />);

    await verifyEmail(user, 'farmer@example.com');

    await user.type(screen.getByLabelText('이메일'), 'x');

    expect(
      screen.queryByText('이메일 인증이 완료되었습니다.'),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '인증코드 발송' }),
    ).toBeInTheDocument();
  });
});
