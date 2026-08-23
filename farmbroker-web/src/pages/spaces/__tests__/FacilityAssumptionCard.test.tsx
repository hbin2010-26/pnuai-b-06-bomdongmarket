import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/auth/AuthProvider';
import { SpacePredictionPage } from '@/pages/spaces/SpacePredictionPage';
import { getProfitEstimates } from '@/services/profitService';
import { createMockProfitEstimates } from '@/mocks/mockProfitEstimates';
import type { SpaceCreateInput } from '@/types/api';

vi.mock('@/services/profitService', () => ({
  getProfitEstimates: vi.fn(),
  getProfitCrops: vi.fn().mockResolvedValue([]),
}));

const input: SpaceCreateInput = {
  title: '부산대 앞 20평 상가 공실',
  address: '부산광역시 금정구 장전동',
  area: 66,
  monthlyRent: 500000,
  floor: 2,
  hasWater: true,
  hasElectricity: true,
  hasVentilation: true,
  description: '테스트용 공실입니다.',
  imageUrls: [],
};

// 이 화면은 등록 폼이 넘긴 location.state 로만 진입할 수 있어, 라우터에 state 를 직접 심는다
// (renderWithProviders 는 경로만 받는다).
function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={[
        { pathname: '/spaces/new/prediction', state: { input, addressParts: null } },
      ]}
    >
      <AuthProvider initialAuthenticated>
        <SpacePredictionPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

// 재배가능비율·천장고는 작물 특성이 아니라 설비 사양이라, 실측 없이는 정할 수 없다.
// 지금까지 코드에 박힌 임의값이었고 화면에서 바꿀 수 없었다(#99).
// 다단 층 수는 1.0.1 부터 작물이 정하므로 여기서 고르지 않는다.
describe('설비 조건 조절', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getProfitEstimates).mockImplementation((request) =>
      Promise.resolve(createMockProfitEstimates(request)),
    );
  });

  it('처음에는 표준 가정값으로 계산한다', async () => {
    renderPage();

    await waitFor(() =>
      expect(getProfitEstimates).toHaveBeenCalledWith({
        area: 66,
        monthlyRent: 500000,
        cultivableRatio: 0.65,
        ceilingHeightM: 2.5,
      }),
    );
    expect(await screen.findByLabelText('재배 가능 바닥 비율')).toHaveValue(0.65);
  });

  it('천장고를 바꾸면 그 값으로 다시 계산한다', async () => {
    const user = userEvent.setup();
    renderPage();

    const ceiling = await screen.findByLabelText('천장고');
    await user.clear(ceiling);
    await user.type(ceiling, '3.5');

    await waitFor(() =>
      expect(getProfitEstimates).toHaveBeenLastCalledWith(
        expect.objectContaining({ ceilingHeightM: 3.5 }),
      ),
    );
  });

  // 층 수는 작물이 정하므로 사용자가 고를 값이 아니다.
  it('다단 층 수는 조절 항목으로 두지 않는다', async () => {
    renderPage();

    await screen.findByLabelText('재배 가능 바닥 비율');
    expect(screen.queryByLabelText('다단 재배대 층 수')).not.toBeInTheDocument();
  });

  it('표준 가정값과 달라지면 되돌리는 버튼이 나온다', async () => {
    const user = userEvent.setup();
    renderPage();

    const ratio = await screen.findByLabelText('재배 가능 바닥 비율');
    expect(
      screen.queryByRole('button', { name: '표준 가정값으로' }),
    ).not.toBeInTheDocument();

    await user.clear(ratio);
    await user.type(ratio, '0.8');

    const reset = await screen.findByRole('button', { name: '표준 가정값으로' });
    await user.click(reset);

    await waitFor(() => expect(ratio).toHaveValue(0.65));
  });
});
