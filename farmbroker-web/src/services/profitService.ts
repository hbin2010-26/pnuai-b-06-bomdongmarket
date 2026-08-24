import { apiRequest, USE_MOCKS } from '@/api/client';
import { ENDPOINTS } from '@/api/endpoints';
import { mockDelay } from '@/mocks/handlers';
import { createMockProfitEstimates, mockProfitCrops } from '@/mocks/mockProfitEstimates';
import type {
  KamisCollectResult,
  ProfitCrop,
  ProfitEstimate,
  ProfitEstimateInput,
} from '@/types/api';

// 등록 전 수익 예측입니다. spaceId가 아직 없으므로 면적·월세만 보내고
// 서버가 지원 작물별 결과를 배분수익 내림차순으로 돌려줍니다(첫 항목이 대표 작물).
export async function getProfitEstimates(
  input: ProfitEstimateInput,
): Promise<ProfitEstimate[]> {
  if (!USE_MOCKS) {
    const response = await apiRequest<ProfitEstimate[]>(ENDPOINTS.profit.estimate, {
      method: 'POST',
      body: input,
    });
    return response.data;
  }

  await mockDelay();
  return createMockProfitEstimates(input);
}

// 계산에 쓸 수 있는 작물 목록입니다. 추천 목록 밖의 작물을 고를 때 여기서 고릅니다.
// 서버가 crop_cultivation_params 테이블을 읽으므로, 행이 늘면 프런트 수정 없이 목록에 들어옵니다.
export async function getProfitCrops(): Promise<ProfitCrop[]> {
  if (!USE_MOCKS) {
    const response = await apiRequest<ProfitCrop[]>(ENDPOINTS.profit.crops);
    return response.data;
  }

  await mockDelay();
  return mockProfitCrops;
}

// KAMIS 시세를 지금 받아옵니다. 매일 04시 배치가 있지만 서버가 그때 꺼져 있으면 건너뛰므로,
// 확인이 필요할 때 직접 부릅니다. 작물마다 외부 API 를 한 번씩 부르느라 몇 초 걸립니다.
export async function collectKamisPrices(): Promise<KamisCollectResult> {
  if (!USE_MOCKS) {
    const response = await apiRequest<KamisCollectResult>(ENDPOINTS.profit.kamisCollect, {
      method: 'POST',
    });
    return response.data;
  }

  await mockDelay();
  return {
    collectedFor: '2026-08-23',
    skipped: false,
    skipReason: null,
    updated: 2,
    missing: 1,
    failed: 0,
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
        surveyedOn: '2026-08-21',
        sampleCount: 5,
      },
      // 비제철이면 조사 자체가 없습니다 — 실패가 아닙니다.
      { cropName: '토마토', status: 'MISSING', pricePerKgKrw: null, surveyedOn: null, sampleCount: null },
    ],
  };
}
