import { createMockProfitEstimates } from '@/mocks/mockProfitEstimates';
import type { AiRecommendation, CropDetail, SpaceDetail } from '@/types/api';

// 1번 공간(66㎡ / 월세 50만원) 기준 계산값입니다. 배분수익 내림차순으로 오며,
// 추천 작물의 선택과 순서를 이 순위에 맞추어 뒤 화면과 상세 화면이 같은 작물을 보여 줍니다.
const mockRecommendationEstimates = createMockProfitEstimates({ area: 66, monthlyRent: 500000 });

export const mockSpaces: SpaceDetail[] = [
  {
    spaceId: 1,
    title: '부산대 앞 20평 상가 공실',
    address: '부산광역시 금정구 장전동',
    area: 66,
    monthlyRent: 500000,
    floor: 2,
    hasWater: true,
    hasElectricity: true,
    hasVentilation: true,
    description:
      '부산대학교 인근의 채광 좋은 2층 상가 공실입니다. 수도와 전기 사용이 안정적이어서 다단 재배 모듈 설치에 적합합니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1530836369250-ef72a3f5cda8?auto=format&fit=crop&w=900&q=80',
    imageUrls: [
      'https://images.unsplash.com/photo-1530836369250-ef72a3f5cda8?auto=format&fit=crop&w=900&q=80',
      'https://images.unsplash.com/photo-1515150144380-bca9f1650ed9?auto=format&fit=crop&w=900&q=80',
    ],
    floorPlanUrls: [
      'https://images.unsplash.com/photo-1503387762-592deb58ef4e?auto=format&fit=crop&w=900&q=80',
    ],
    status: 'AVAILABLE',
    owner: { userId: 1, nickname: '그린스페이스랩' },
    createdAt: '2026-06-29T15:00:00',
    updatedAt: '2026-06-29T15:00:00',
  },
  {
    spaceId: 2,
    title: '서면 지하 재배 공간',
    address: '부산광역시 부산진구 서면',
    area: 42,
    monthlyRent: 350000,
    floor: -1,
    hasWater: true,
    hasElectricity: true,
    hasVentilation: false,
    description: '환기 키트 보강 후 허브와 새싹채소 재배에 적합한 소형 지하 공간입니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1492496913980-501348b61469?auto=format&fit=crop&w=900&q=80',
    imageUrls: [
      'https://images.unsplash.com/photo-1492496913980-501348b61469?auto=format&fit=crop&w=900&q=80',
    ],
    floorPlanUrls: [
      'https://images.unsplash.com/photo-1487958449943-2429e8be8625?auto=format&fit=crop&w=900&q=80',
    ],
    status: 'AVAILABLE',
    owner: { userId: 3, nickname: '서면공간주' },
    createdAt: '2026-06-25T10:20:00',
    updatedAt: '2026-06-28T13:30:00',
  },
  {
    spaceId: 3,
    title: '해운대 루프탑 온실',
    address: '부산광역시 해운대구 우동',
    area: 95,
    monthlyRent: 920000,
    floor: 7,
    hasWater: true,
    hasElectricity: true,
    hasVentilation: true,
    description:
      '일조량이 풍부한 루프탑 공간으로 프리미엄 잎채소 재배와 체험형 판매를 함께 운영하기 좋습니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=900&q=80',
    imageUrls: [
      'https://images.unsplash.com/photo-1523348837708-15d4a09cfac2?auto=format&fit=crop&w=900&q=80',
      'https://images.unsplash.com/photo-1501004318641-b39e6451bec6?auto=format&fit=crop&w=900&q=80',
    ],
    floorPlanUrls: [
      'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=900&q=80',
    ],
    status: 'MATCHED',
    owner: { userId: 4, nickname: '루프앤루츠' },
    createdAt: '2026-06-21T09:00:00',
    updatedAt: '2026-07-01T11:15:00',
  },
  {
    spaceId: 4,
    title: '명륜역 인근 1층 창고',
    address: '부산광역시 동래구 명륜동',
    area: 58,
    monthlyRent: 430000,
    floor: 1,
    hasWater: false,
    hasElectricity: true,
    hasVentilation: true,
    description:
      '접근성이 좋은 1층 창고형 공간입니다. 재배 시작 전 급수 탱크 모듈 설치를 권장합니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=900&q=80',
    imageUrls: [
      'https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&w=900&q=80',
    ],
    floorPlanUrls: [
      'https://images.unsplash.com/photo-1503387762-592deb58ef4e?auto=format&fit=crop&w=900&q=80',
    ],
    status: 'AVAILABLE',
    owner: { userId: 5, nickname: '동래스페이스' },
    createdAt: '2026-06-18T12:40:00',
    updatedAt: '2026-06-19T08:10:00',
  },
];

export const mockCrops: CropDetail[] = [
  {
    cropId: 3,
    name: '버터헤드 상추',
    category: '잎채소',
    difficulty: 'EASY',
    growingPeriodDays: 30,
    optimalTempMin: 15,
    optimalTempMax: 22,
    optimalHumidity: 65,
    lightRequirement: 'MEDIUM',
    yieldPerSqmKg: 3.5,
    avgPricePerKg: 7000,
    description:
      '재배 주기가 짧고 지역 수요가 안정적이며 품질 관리가 쉬운 대표 실내 작물입니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1?auto=format&fit=crop&w=900&q=80',
    dataSource: 'SEED',
  },
  {
    cropId: 5,
    name: '바질',
    category: '허브',
    difficulty: 'NORMAL',
    growingPeriodDays: 42,
    optimalTempMin: 20,
    optimalTempMax: 27,
    optimalHumidity: 60,
    lightRequirement: 'HIGH',
    yieldPerSqmKg: 1.7,
    avgPricePerKg: 30000,
    description: '레스토랑 납품과 로컬 구독 박스에 적합한 고단가 허브 작물입니다.',
    imageUrl:
      'https://images.unsplash.com/photo-1618375569909-3c8616cf7733?auto=format&fit=crop&w=900&q=80',
    dataSource: 'SEED',
  },
];

export const mockRecommendation: AiRecommendation = {
  recommendationId: 1,
  spaceId: 1,
  // 작물 선택과 순서는 서버 수익 계산기가 정하고, 작물마다 그 작물 기준 계산값이 함께 옵니다.
  // 목업도 계산기가 지원하는 작물로 맞춥니다 — 예전 목업의 '버터헤드 상추'는 계산기에 없어
  // 화면에는 작물이 뜨는데 금액은 비는 상태를 재현하고 있었습니다.
  recommendedCrops: [
    {
      cropName: '딸기',
      cropId: 4,
      pickType: 'PROFIT' as const,
      reason:
        '서버 계산 기준 배분수익이 가장 큽니다. 단가가 높아 같은 재배면적에서 매출이 크게 잡힙니다.',
      expectedYieldKg: 33,
      avgPricePerKg: 30000,
      profitEstimate: mockRecommendationEstimates[0] ?? null,
    },
    {
      cropName: '바질',
      cropId: 5,
      pickType: 'PROFIT' as const,
      reason:
        '단가가 높고 좁은 간격으로 재배할 수 있어 환기만 보완하면 수익성을 높일 수 있습니다.',
      expectedYieldKg: 12,
      avgPricePerKg: 20000,
      profitEstimate: mockRecommendationEstimates[1] ?? null,
    },
    {
      cropName: '상추',
      cropId: 3,
      pickType: 'PREFERENCE' as const,
      reason: '재배 주기가 짧고 조명 요구량이 높지 않아 처음 길러 보기에 좋습니다.',
      expectedYieldKg: 80,
      avgPricePerKg: 8000,
      profitEstimate: mockRecommendationEstimates[2] ?? null,
    },
  ],
  cautions: [
    '습도가 높은 주간에는 환기 상태를 매일 확인해야 합니다.',
    '초기에는 저전력 LED 구역부터 운영한 뒤 선반 수를 단계적으로 늘리는 편이 안전합니다.',
  ],
  createdAt: '2026-07-05T14:00:00',
  // 첫 추천 작물의 계산값. 작물별 값은 recommendedCrops[].profitEstimate 에 있습니다.
  profitEstimate: mockRecommendationEstimates[0] ?? null,
};
