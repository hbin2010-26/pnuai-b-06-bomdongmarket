import { useCallback, useEffect, useState } from 'react';

import { getProfitEstimates } from '@/services/profitService';
import { createSpace } from '@/services/spaceService';
import {
  FACILITY_DEFAULTS,
  type FacilityAssumptions,
} from '@/pages/spaces/components/FacilityAssumptionCard';
import type { ProfitEstimate, SpaceCreateInput } from '@/types/api';
import type { AsyncStatus } from '@/types/common';

// 등록 확인 단계는 수익 예측 조회와 실제 등록 요청을 각각 다른 상태로 다룹니다.
//
// 설비 조건(재배가능비율·층수·천장고)은 화면에서 조절할 수 있고, 바뀌면 예측을 다시 받습니다.
// 이 값은 공간 등록 내용에는 들어가지 않습니다 — 실측이 아니라 계산 가정이기 때문입니다.
export function useSpaceRegistration(input: SpaceCreateInput) {
  const [estimates, setEstimates] = useState<ProfitEstimate[]>([]);
  const [predictionStatus, setPredictionStatus] = useState<AsyncStatus>('idle');
  const [saveStatus, setSaveStatus] = useState<AsyncStatus>('idle');
  const [saveError, setSaveError] = useState<string | null>(null);
  const [facility, setFacility] = useState<FacilityAssumptions>({ ...FACILITY_DEFAULTS });

  const { area, monthlyRent } = input;
  const { cultivableRatio, ceilingHeightM } = facility;

  const loadPrediction = useCallback(async () => {
    setPredictionStatus('loading');

    try {
      const result = await getProfitEstimates({
        area,
        monthlyRent,
        cultivableRatio,
        ceilingHeightM,
      });
      setEstimates(result);
      setPredictionStatus(result.length > 0 ? 'success' : 'error');
    } catch {
      setPredictionStatus('error');
    }
  }, [area, monthlyRent, cultivableRatio, ceilingHeightM]);

  const submit = useCallback(async () => {
    setSaveStatus('loading');
    setSaveError(null);

    try {
      await createSpace(input);
      setSaveStatus('success');
    } catch (caught) {
      setSaveError(
        caught instanceof Error ? caught.message : '공간을 등록하지 못했습니다.',
      );
      setSaveStatus('error');
    }
  }, [input]);

  useEffect(() => {
    void loadPrediction();
  }, [loadPrediction]);

  return {
    estimates,
    predictionStatus,
    saveStatus,
    saveError,
    facility,
    setFacility,
    reloadPrediction: loadPrediction,
    submit,
  };
}
