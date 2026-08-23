import { apiRequest, USE_MOCKS } from '@/api/client';
import { ENDPOINTS } from '@/api/endpoints';
import { mockDelay } from '@/mocks/handlers';
import { createMockProfitEstimates, mockProfitCrops } from '@/mocks/mockProfitEstimates';
import type { ProfitCrop, ProfitEstimate, ProfitEstimateInput } from '@/types/api';

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
