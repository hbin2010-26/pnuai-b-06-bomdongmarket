import { useCallback, useEffect, useState } from 'react';

import { getRecommendation, getSpaceDetail } from '@/services/spaceService';
import type { AiRecommendation, AiRecommendationInput, SpaceDetail } from '@/types/api';
import type { AsyncStatus } from '@/types/common';

// 상세 페이지의 공간 조회와 AI 추천 조회를 분리해 각 상태를 독립적으로 표시합니다.
export function useSpaceDetail(spaceId: number) {
  const [space, setSpace] = useState<SpaceDetail | null>(null);
  const [recommendation, setRecommendation] = useState<AiRecommendation | null>(null);
  const [status, setStatus] = useState<AsyncStatus>('idle');
  const [recommendationStatus, setRecommendationStatus] = useState<AsyncStatus>('idle');
  const [recommendationError, setRecommendationError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setStatus('loading');
    setError(null);

    try {
      const result = await getSpaceDetail(spaceId);
      setSpace(result);
      setStatus('success');
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : '공간 정보를 불러오지 못했습니다',
      );
      setStatus('error');
    }
  }, [spaceId]);

  const loadRecommendation = useCallback(
    async (request: Omit<AiRecommendationInput, 'spaceId'> = {}) => {
      setRecommendationStatus('loading');
      setRecommendationError(null);

      try {
        const result = await getRecommendation(spaceId, request);
        setRecommendation(result);
        setRecommendationStatus('success');
      } catch (caught) {
        // 이유를 버리면 화면이 입력 폼으로 조용히 돌아가 사용자가 같은 버튼을 다시 누릅니다.
        // "계산할 수 있는 작물이 없다"처럼 다시 눌러도 달라지지 않는 실패가 있어 이유를 남깁니다.
        setRecommendationError(
          caught instanceof Error ? caught.message : 'AI 추천을 실행하지 못했습니다.',
        );
        setRecommendationStatus('error');
      }
    },
    [spaceId],
  );

  // 조건을 다시 잡을 수 있게 추천만 비웁니다. 공간 정보는 그대로 두어야
  // 조건 입력 화면으로 돌아가도 면적·월세가 다시 로딩되지 않습니다.
  const clearRecommendation = useCallback(() => {
    setRecommendation(null);
    setRecommendationStatus('idle');
    setRecommendationError(null);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return {
    space,
    recommendation,
    status,
    recommendationStatus,
    recommendationError,
    error,
    reload: load,
    loadRecommendation,
    clearRecommendation,
  };
}
