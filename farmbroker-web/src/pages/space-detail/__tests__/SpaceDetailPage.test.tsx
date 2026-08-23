import { cleanup, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { clearAuthSession, saveAuthSession } from '@/auth/session';
import { applyMatching } from '@/services/matchingService';
import { getRecommendation } from '@/services/spaceService';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SpaceDetailPage } from '@/pages/space-detail/SpaceDetailPage';
import type { AiRecommendation, ProfitEstimate } from '@/types/api';

// 상세 화면의 매칭 카드가 내 신청 여부를 조회하므로 조회·취소도 함께 대역을 둔다.
vi.mock('@/services/matchingService', () => ({
  applyMatching: vi.fn(),
  cancelMatching: vi.fn(),
  getMyMatchings: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/services/spaceService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/spaceService')>()),
  getRecommendation: vi.fn(),
}));
// 수익 카드가 작물 선택지를 서버에서 받아 옵니다 — 선택지가 없으면 작물을 고를 수 없습니다.
vi.mock('@/services/profitService', () => ({
  getProfitCrops: vi.fn().mockResolvedValue([profitCrop('바질'), profitCrop('상추')]),
  getProfitEstimates: vi.fn().mockResolvedValue([]),
}));

// 서버는 가나다순으로 내려줍니다. 계산 가능 여부와 값의 성격만 화면이 씁니다.
function profitCrop(cropName: string) {
  return {
    cropName,
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: null,
    pricePerKgKrw: 8000,
  };
}

function LocationProbe() {
  return <output>{useLocation().pathname}</output>;
}

// 작물마다 그 작물 기준 계산값이 함께 온다. 매출만 서로 다르게 두어 화면이 어느 작물의
// 숫자를 보여 주는지 구분한다.
function estimate(cropName: string, monthlyRevenueKrw: number): ProfitEstimate {
  return {
    cropName,
    totalAreaM2: 66,
    cultivableRatio: 0.65,
    areaUtilizationPercent: 65,
    moduleLayers: 4,
    ceilingHeightM: 2.5,
    availableFloorAreaM2: 42.9,
    cultivationAreaM2: 171.6,
    lightingPowerW: 11316,
    averageMonthlyEnergyKwh: 9200,
    monthlyTotalProductionKg: 475,
    monthlySalesKg: 427,
    pricePerKgKrw: 8000,
    priceSource: 'SEED',
    priceBasisDate: '2026-07-04',
    monthlyRevenueKrw,
    electricityCostKrw: 1420000,
    waterCostKrw: 6000,
    seedlingCostKrw: 858000,
    nutrientSolutionL: 18121,
    nutrientCostKrw: 362419,
    materialCostKrw: 1220419,
    laborCostKrw: 2450000,
    equipmentRentalCostKrw: 943800,
    otherCostKrw: 300000,
    monthlyOperatingCostKrw: 5276000,
    monthlyOperatingProfitKrw: monthlyRevenueKrw - 5276000,
    landlordShareRatio: 0.8,
    landlordExpectedIncomeKrw: Math.round((monthlyRevenueKrw - 5276000) * 0.8),
    desiredMonthlyRentKrw: 500000,
    businessOperatingProfitKrw: Math.round((monthlyRevenueKrw - 5276000) * 0.2),
    operatingLoss: monthlyRevenueKrw < 5276000,
    longTermRecommended: false,
    recommendation: '개인취미 대여 방식 추천',
    contractType: '단기계약형',
  };
}

function recommendation(): AiRecommendation {
  return {
    recommendationId: 1,
    spaceId: 1,
    recommendedCrops: [
      {
        cropId: 1,
        cropName: '상추',
        pickType: 'PROFIT' as const,
        reason: '공간 조건에 적합합니다.',
        expectedYieldKg: 10,
        avgPricePerKg: 1000,
        profitEstimate: estimate('상추', 1000000),
      },
      {
        cropId: 2,
        cropName: '바질',
        // 계산기 순위 밖에서 취향으로 고른 한 칸입니다(#98).
        pickType: 'PREFERENCE' as const,
        reason: '단가가 높습니다.',
        expectedYieldKg: 5,
        avgPricePerKg: 20000,
        profitEstimate: estimate('바질', 2000000),
      },
    ],
    cautions: [],
    createdAt: '2026-08-05T00:00:00',
    profitEstimate: estimate('상추', 1000000),
  };
}

afterEach(() => {
  cleanup();
  clearAuthSession();
  vi.clearAllMocks();
});

describe('SpaceDetailPage', () => {
  it('비로그인 사용자가 AI 추천을 실행하면 로그인으로 이동하고 API를 호출하지 않는다', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <SpaceDetailPage />
        <LocationProbe />
      </>,
      { route: '/spaces/1' },
    );

    expect(
      await screen.findByRole('heading', {
        name: /부산대 앞 20평 상가 공실/i,
      }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /AI 추천 실행/i }));
    expect(screen.getByText('/login')).toBeInTheDocument();
    expect(getRecommendation).not.toHaveBeenCalled();
  });

  it('로그인한 사용자는 AI 추천 API를 실행할 수 있다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await user.click(await screen.findByRole('button', { name: /AI 추천 실행/i }));

    // 요청을 비워 두면 서버 계산기 순위를 그대로 따른다.
    expect(getRecommendation).toHaveBeenCalledWith(1, {});
    expect(await screen.findByText('공간 조건에 적합합니다.')).toBeInTheDocument();
  });

  // 프롬프트의 [사용자 요청]이 늘 비어 있어 모델이 무엇을 중시할지 정할 근거가 없었다(#98).
  it('입력한 사용자 요청을 추천 API로 함께 보낸다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    // 작물과 목적은 고르는 값입니다 — 자유 입력이면 표현이 제각각이라 AI 해석이 흔들립니다.
    // 선택지는 서버에서 받아 오므로 도착할 때까지 기다립니다.
    await screen.findByRole('option', { name: '바질' });
    await user.selectOptions(screen.getByLabelText('궁금한 작물'), '바질');
    await user.click(screen.getByRole('radio', { name: '수익형' }));
    await user.type(
      screen.getByLabelText('기타 취향 및 요청사항'),
      '초기 비용을 줄이고 싶습니다.',
    );
    await user.click(screen.getByRole('button', { name: /AI 추천 실행/i }));

    expect(getRecommendation).toHaveBeenCalledWith(1, {
      preferredCrop: '바질',
      purpose: '수익형',
      additionalInfo: '초기 비용을 줄이고 싶습니다.',
    });
  });

  // 계산기 순위와 취향 추천이 섞여 보이면 어느 쪽이 수익 근거인지 알 수 없습니다(#98).
  it('취향으로 고른 작물은 순위 번호 대신 취향 추천으로 표시한다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await user.click(await screen.findByRole('button', { name: /AI 추천 실행/i }));

    expect(await screen.findByRole('button', { name: '1순위 상추' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '취향 추천 바질' })).toBeInTheDocument();
  });

  // 조건을 잘못 넣었을 때 페이지를 새로 고치는 것 말고는 되돌릴 길이 없었습니다(PR #130 리뷰).
  it('추천 조건 다시 설정을 누르면 입력 화면으로 돌아간다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await user.click(await screen.findByRole('button', { name: /AI 추천 실행/i }));
    await user.click(await screen.findByRole('button', { name: /추천 조건 다시 설정/ }));

    expect(await screen.findByLabelText('궁금한 작물')).toBeInTheDocument();
  });

  // 총액만 보면 무엇을 줄여야 할지 알 수 없어 비용 구성을 함께 보여줍니다(PR #130 리뷰).
  it('운영비를 항목별로 나눠 보여준다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await user.click(await screen.findByRole('button', { name: /AI 추천 실행/i }));

    expect(await screen.findByText('월 운영비 구성')).toBeInTheDocument();
    // 가장 큰 항목이 인건비(245만 / 634만 = 38.6%)라, 그 비중이 보여야 합니다.
    expect(screen.getByText('인건비')).toBeInTheDocument();
    expect(screen.getByText('38.6%')).toBeInTheDocument();
  });

  // 추천이 3개 떠도 수익은 1개로 좁혀져, 상단 작물과 아래 금액이 어긋났다(#98).
  it('추천 작물을 고르면 그 작물 기준 계산값으로 바뀐다', async () => {
    const user = userEvent.setup();
    saveAuthSession({
      userId: 3,
      email: 'consumer@example.com',
      nickname: '지역소비자',
      roles: ['CONSUMER'],
    });
    vi.mocked(getRecommendation).mockResolvedValue(recommendation());
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await user.click(await screen.findByRole('button', { name: /AI 추천 실행/i }));

    // 1순위가 먼저 선택돼 있고, 그 작물의 매출이 보인다.
    const first = await screen.findByRole('button', { name: '1순위 상추' });
    expect(first).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('₩1,000,000')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '취향 추천 바질' }));

    expect(screen.getByRole('button', { name: '취향 추천 바질' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByText('₩2,000,000')).toBeInTheDocument();
  });

  it('농부에게 해당 공간의 신청 화면으로 가는 경로를 제공한다', async () => {
    saveAuthSession({
      userId: 2,
      email: 'farmer@example.com',
      nickname: '도시농부',
      roles: ['FARMER'],
    });
    renderWithProviders(<SpaceDetailPage />, { route: '/spaces/1' });

    await screen.findByRole('heading', { name: /부산대 앞 20평 상가 공실/i });

    expect(await screen.findByRole('link', { name: /매칭 신청하기/i })).toHaveAttribute(
      'href',
      '/spaces/1/apply',
    );
    // 신청 자체는 신청 화면에서만 일어난다 — 상세 화면은 이동 경로만 제공한다.
    expect(applyMatching).not.toHaveBeenCalled();
  });
});
