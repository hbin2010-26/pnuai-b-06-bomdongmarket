import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { clearAuthSession, saveAuthSession } from '@/auth/session';
import { Header } from '@/components/layout/Header';
import { APP_INFO } from '@/constants/appInfo';
import { PRIMARY_NAVIGATION } from '@/constants/navigation';
import { ROUTES } from '@/constants/routes';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('Header', () => {
  beforeEach(() => clearAuthSession());

  it('FarmBroker 심벌과 제품명을 홈 링크로 제공한다', () => {
    const { getByRole } = renderWithProviders(<Header />);
    const brandLink = getByRole('link', { name: `${APP_INFO.name} 홈으로 이동` });

    expect(brandLink).toHaveAttribute('href', ROUTES.home);
    expect(within(brandLink).getByText(APP_INFO.name)).toBeInTheDocument();
    expect(brandLink.querySelector('img')).toHaveAttribute(
      'src',
      '/brand/farmbroker-symbol.png',
    );
  });

  it('데스크톱 내비게이션에 넓은 간격과 클릭 영역을 제공한다', () => {
    const { getByRole } = renderWithProviders(<Header />);
    expect(getByRole('banner')).toHaveAttribute('data-build', 'auth-header-v2');
    const navigation = getByRole('navigation', { name: '주요 내비게이션' });

    expect(navigation).toHaveClass('gap-2', 'xl:gap-3');

    PRIMARY_NAVIGATION.forEach((item) => {
      const link = within(navigation).getByRole('link', { name: item.label });
      expect(link).toHaveAttribute('href', item.href);
      expect(link).toHaveClass('min-h-11', 'px-4', 'xl:px-5');
    });
  });

  it('비로그인 상태에서는 화면 크기와 관계없이 로그인 링크를 표시한다', () => {
    const { getByRole, queryByRole } = renderWithProviders(<Header />);
    const loginLink = getByRole('link', { name: '로그인' });

    expect(loginLink).toHaveAttribute('href', ROUTES.login);
    expect(loginLink).not.toHaveClass('hidden');
    expect(queryByRole('link', { name: /공간 등록|등록/ })).not.toBeInTheDocument();
  });

  it('로그인 상태에서는 로그인 대신 사용자 닉네임을 표시한다', () => {
    saveAuthSession({
      userId: 2,
      email: 'farmer@example.com',
      nickname: '도시농부',
      roles: ['FARMER'],
    });

    const { getByRole, queryByRole } = renderWithProviders(<Header />, {
      authenticated: true,
    });

    expect(getByRole('link', { name: '도시농부 마이페이지' })).toHaveAttribute(
      'href',
      ROUTES.myPage,
    );
    expect(queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
    expect(queryByRole('link', { name: /공간 등록|등록/ })).not.toBeInTheDocument();
    expect(getByRole('button', { name: '알림' })).toBeInTheDocument();
  });

  it('알림을 닫으면 헤더의 알림 버튼으로 포커스를 돌려준다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 1,
      email: 'owner@example.com',
      nickname: '그린스페이스랩',
      roles: ['OWNER'],
    });
    renderWithProviders(<Header />, { authenticated: true });

    const notificationButton = await screen.findByRole('button', {
      name: '알림, 응답 대기 2건',
    });
    await user.click(notificationButton);

    expect(
      screen.getByRole('dialog', { name: '받은 신청과 보낸 신청' }),
    ).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(notificationButton).toHaveFocus();
  });
});

// "다 확인했다"는 뜻이므로, 한 번 열어 본 신청은 배지에서 빠져야 합니다.
describe('Header 알림 배지', () => {
  beforeEach(() => {
    clearAuthSession();
    window.sessionStorage.clear();
  });

  it('알림창을 열면 배지가 사라진다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 1,
      email: 'owner@example.com',
      nickname: '그린스페이스랩',
      roles: ['OWNER'],
    });
    renderWithProviders(<Header />, { authenticated: true });

    await user.click(await screen.findByRole('button', { name: '알림, 응답 대기 2건' }));

    expect(screen.getByRole('button', { name: '알림' })).toBeInTheDocument();
  });

  // 매칭 번호는 신청자와 공간 제공자가 함께 쓴다. 한 탭에서 계정을 바꿨을 때
  // 앞 계정이 본 신청 때문에 새 계정의 배지가 숨으면 안 된다.
  it('같은 탭에서 계정을 바꾸면 앞 계정의 읽음 표시를 쓰지 않는다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 1,
      email: 'owner@example.com',
      nickname: '그린스페이스랩',
      roles: ['OWNER'],
    });
    renderWithProviders(<Header />, { authenticated: true });

    await user.click(await screen.findByRole('button', { name: '알림, 응답 대기 2건' }));
    expect(screen.getByRole('button', { name: '알림' })).toBeInTheDocument();

    act(() => {
      saveAuthSession({
        userId: 2,
        email: 'other-owner@example.com',
        nickname: '다른공간랩',
        roles: ['OWNER'],
      });
    });

    expect(
      await screen.findByRole('button', { name: '알림, 응답 대기 2건' }),
    ).toBeInTheDocument();
  });
});
