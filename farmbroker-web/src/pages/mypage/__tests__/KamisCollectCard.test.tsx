import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { KamisCollectCard } from '@/pages/mypage/components/KamisCollectCard';
import { collectKamisPrices } from '@/services/profitService';
import { renderWithProviders } from '@/test/renderWithProviders';
import type { KamisCollectResult } from '@/types/api';

vi.mock('@/services/profitService', () => ({
  collectKamisPrices: vi.fn(),
}));

function result(overrides: Partial<KamisCollectResult> = {}): KamisCollectResult {
  return {
    collectedFor: '2026-08-23',
    skipped: false,
    skipReason: null,
    updated: 0,
    missing: 0,
    failed: 0,
    items: [],
    ...overrides,
  };
}

// 눌렀을 때 늘 "연동되었습니다"만 뜨면, 실제로 아무것도 못 받아온 날에도 성공처럼 보인다.
// 몇 종을 받았고 언제 조사된 값인지가 문구에 남아야 한다.
describe('농산물 시세 갱신', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('받아온 종수와 조사 기준일을 알려준다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockResolvedValue(
      result({
        updated: 2,
        items: [
          {
            cropName: '상추',
            status: 'UPDATED',
            pricePerKgKrw: 8750,
            surveyedOn: '2026-08-21',
            sampleCount: 9,
          },
          {
            cropName: '시금치',
            status: 'UPDATED',
            pricePerKgKrw: 13500,
            surveyedOn: '2026-08-20',
            sampleCount: 5,
          },
        ],
      }),
    );
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('2종');
    // 가장 최근 조사일을 밝혀 며칠 전 값인지 알 수 있어야 한다.
    expect(status).toHaveTextContent('2026-08-21 조사 기준');
  });

  it('받아온 게 없으면 성공처럼 말하지 않는다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockResolvedValue(result({ updated: 0, missing: 19 }));
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('새로 받아올 시세가 없습니다');
    expect(status).not.toHaveTextContent('받아왔습니다');
  });

  it('건너뛴 경우를 그대로 알린다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockResolvedValue(
      result({ skipped: true, skipReason: 'DISABLED' }),
    );
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));

    expect(await screen.findByRole('status')).toHaveTextContent('지금은 받아올 수 없습니다');
  });

  // 건너뛴 이유가 셋인데 문구가 하나면 왜 안 됐는지 알 수 없다(#129 리뷰).
  it('최소 간격에 걸린 경우와 이미 받아오는 중인 경우를 구분해 알린다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockResolvedValue(
      result({ skipped: true, skipReason: 'COOLDOWN' }),
    );
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('조금 전에 받아왔습니다');

    vi.mocked(collectKamisPrices).mockResolvedValue(
      result({ skipped: true, skipReason: 'ALREADY_RUNNING' }),
    );
    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));
    expect(await screen.findByRole('status')).toHaveTextContent('이미 받아오는 중입니다');
  });

  // 전부 실패했는데 갱신 0건을 먼저 보면 장애가 "받아올 시세가 없음"으로 읽힌다(#129 리뷰).
  it('모두 실패했을 때 조사 없음처럼 말하지 않는다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockResolvedValue(
      result({
        updated: 0,
        failed: 19,
        items: [
          {
            cropName: '상추',
            status: 'QUERY_FAILED',
            pricePerKgKrw: null,
            surveyedOn: null,
            sampleCount: null,
          },
        ],
      }),
    );
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('받아오지 못했습니다');
    expect(status).toHaveTextContent('19종 실패');
    expect(status).not.toHaveTextContent('새로 받아올 시세가 없습니다');
  });

  it('실패하면 이유를 보여준다', async () => {
    const user = userEvent.setup();
    vi.mocked(collectKamisPrices).mockRejectedValue(new Error('서버에 연결하지 못했습니다.'));
    renderWithProviders(<KamisCollectCard />, { authenticated: true });

    await user.click(screen.getByRole('button', { name: /시세 갱신/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결하지 못했습니다.');
  });
});
