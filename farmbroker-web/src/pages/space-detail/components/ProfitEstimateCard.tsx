import { Bot, ChartNoAxesCombined, Info } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { LoadingState } from '@/components/common/LoadingState';
import { getProfitCrops, getProfitEstimates } from '@/services/profitService';
import type {
  AiRecommendation,
  AiRecommendationInput,
  ProfitCrop,
  ProfitEstimate,
} from '@/types/api';
import type { AsyncStatus } from '@/types/common';
import { cn } from '@/utils/cn';
import { formatCurrency, formatNumber } from '@/utils/format';

// 추천 실행 시 함께 보낼 사용자 요청입니다. 세 값 모두 선택 입력입니다.
type UserRequest = Omit<AiRecommendationInput, 'spaceId'>;

interface ProfitEstimateCardProps {
  recommendation: AiRecommendation | null;
  status: AsyncStatus;
  onRun: (request: UserRequest) => void;
  // 추천 목록 밖의 작물을 계산할 때 서버에 다시 보낼 공간 조건입니다.
  area: number;
  monthlyRent: number;
}

function profitTone(value: number) {
  return value < 0 ? 'text-feedback-danger' : 'text-content';
}

// 값이 추정값인지 알려 줍니다. 재배 파라미터가 아직 전부 추정값이라(#99)
// 숫자만 보여 주면 실측처럼 읽힙니다.
function isEstimateData(dataStatus: string) {
  return dataStatus === 'MVP_ESTIMATE';
}

// AI 추천 결과와 서버 계산 수익을 보여주는 상세 페이지 보조 패널입니다.
//
// 금액은 모두 서버가 계산한 값을 그대로 씁니다. 예전에는 이 화면이 매출의 34%를 운영비로
// 잡아 직접 만들어 썼는데, 그 0.34의 근거가 코드에 없었고 서버가 전력·용수·재료·인건비까지
// 계산해 내려주는 값은 쓰이지 않았습니다(#98).
export function ProfitEstimateCard({
  recommendation,
  status,
  onRun,
  area,
  monthlyRent,
}: ProfitEstimateCardProps) {
  const [request, setRequest] = useState<UserRequest>({});
  // 화면에서 고른 작물. 추천 목록 안이면 그 작물의 계산값을 그대로 쓰고,
  // 밖이면 그 작물만 다시 계산해 받습니다.
  const [selectedCrop, setSelectedCrop] = useState<string | null>(null);
  const [otherCrops, setOtherCrops] = useState<ProfitCrop[]>([]);
  const [otherEstimate, setOtherEstimate] = useState<ProfitEstimate | null>(null);
  const [otherStatus, setOtherStatus] = useState<AsyncStatus>('idle');

  // 추천이 없을 때 매번 새 빈 배열을 만들면 아래 효과가 렌더마다 다시 돌아 선택이 풀립니다.
  const recommendedCrops = useMemo(
    () => recommendation?.recommendedCrops ?? [],
    [recommendation],
  );

  // 추천을 새로 받으면 첫 작물로 돌아갑니다(계산기 순위의 1순위입니다).
  useEffect(() => {
    setSelectedCrop(recommendedCrops[0]?.cropName ?? null);
    setOtherEstimate(null);
    setOtherStatus('idle');
  }, [recommendedCrops]);

  // 추천 목록 밖의 작물을 고를 수 있게 목록을 받아 둡니다.
  // 서버가 DB를 읽으므로 작물이 늘면 이 화면은 그대로 두어도 늘어납니다.
  useEffect(() => {
    if (!recommendation) return;
    let alive = true;
    getProfitCrops()
      .then((crops) => {
        if (alive) setOtherCrops(crops);
      })
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, [recommendation]);

  const selectedRecommendation = recommendedCrops.find(
    (crop) => crop.cropName === selectedCrop,
  );
  const estimate = selectedRecommendation?.profitEstimate ?? otherEstimate;
  const selectedCropData = useMemo(
    () => otherCrops.find((crop) => crop.cropName === selectedCrop) ?? null,
    [otherCrops, selectedCrop],
  );

  // 추천 목록 밖의 작물은 서버에 그 작물만 계산해 달라고 다시 묻습니다.
  function handlePickOtherCrop(cropName: string) {
    if (!cropName) return;
    setSelectedCrop(cropName);
    const inRecommendation = recommendedCrops.some((crop) => crop.cropName === cropName);
    if (inRecommendation) {
      setOtherEstimate(null);
      setOtherStatus('idle');
      return;
    }
    setOtherStatus('loading');
    setOtherEstimate(null);
    getProfitEstimates({ area, monthlyRent, cropNames: [cropName] })
      .then((results) => {
        setOtherEstimate(results[0] ?? null);
        setOtherStatus(results.length > 0 ? 'success' : 'error');
      })
      .catch(() => setOtherStatus('error'));
  }

  if (status === 'loading') {
    return <LoadingState label="AI 추천을 실행하는 중입니다" />;
  }

  return (
    <Card padding="lg">
      <div className="flex items-center justify-between gap-4">
        <div>
          <Badge tone="blue">AI 추천</Badge>
          <h2 className="mt-3 text-xl font-black text-ink-900">수익성과 작물 적합도</h2>
        </div>
        <Bot className="h-9 w-9 text-leaf-700" aria-hidden />
      </div>

      {!recommendation ? (
        <div className="mt-5">
          <p className="text-sm leading-6 text-slate-600">
            작물의 선택과 순서는 서버 수익 계산기가 정하고, AI는 각 작물이 이 공간에 왜 맞는지
            근거를 씁니다. 아래를 채우면 그 조건에 맞춰 추천이 달라집니다. 비워 두면 계산기
            순위를 그대로 따릅니다.
          </p>

          <div className="mt-5 grid gap-3">
            <Input
              label="희망 작물"
              maxLength={30}
              onChange={(event) =>
                setRequest((prev) => ({ ...prev, preferredCrop: event.target.value }))
              }
              placeholder="예: 상추"
              value={request.preferredCrop ?? ''}
            />
            <Input
              label="재배 목적"
              maxLength={100}
              onChange={(event) =>
                setRequest((prev) => ({ ...prev, purpose: event.target.value }))
              }
              placeholder="예: 소규모 부업"
              value={request.purpose ?? ''}
            />
            <Input
              label="추가 조건"
              maxLength={500}
              onChange={(event) =>
                setRequest((prev) => ({ ...prev, additionalInfo: event.target.value }))
              }
              placeholder="예: 초기 비용을 최대한 줄이고 싶습니다."
              value={request.additionalInfo ?? ''}
            />
          </div>

          <Button className="mt-5 w-full" onClick={() => onRun(request)}>
            <ChartNoAxesCombined className="h-5 w-5" aria-hidden />
            AI 추천 실행
          </Button>
        </div>
      ) : (
        <div className="mt-5">
          {/* 추천 작물을 버튼으로 두고, 누르면 그 작물 기준 계산값으로 바뀝니다.
              예전에는 상단에 뜬 작물과 아래 금액의 작물이 서로 달랐습니다. */}
          <div role="group" aria-label="추천 작물" className="flex flex-wrap gap-2">
            {recommendedCrops.map((crop, index) => {
              const selected = crop.cropName === selectedCrop;
              return (
                <button
                  aria-pressed={selected}
                  className={cn(
                    'rounded-app px-3 py-2 text-sm font-bold transition duration-ui',
                    selected
                      ? 'bg-leaf-700 text-white'
                      : 'border border-leaf-200 bg-white text-slate-600 hover:bg-leaf-50',
                  )}
                  key={crop.cropName}
                  onClick={() => handlePickOtherCrop(crop.cropName)}
                  type="button"
                >
                  {index + 1}순위 {crop.cropName}
                </button>
              );
            })}
          </div>

          {/* 추천 밖 작물도 계산해 볼 수 있어야 합니다 — 추천이 곧 선택지 전부는 아닙니다. */}
          {otherCrops.length > 0 ? (
            <label className="mt-3 block text-xs font-semibold text-slate-500">
              다른 작물로 계산
              <select
                className="mt-1 block w-full rounded-app border border-leaf-200 px-3 py-2 text-base font-medium text-ink-900"
                onChange={(event) => handlePickOtherCrop(event.target.value)}
                value={selectedCrop ?? ''}
              >
                {recommendedCrops.every((crop) => crop.cropName !== selectedCrop) &&
                selectedCrop ? null : (
                  <option value="">작물 선택</option>
                )}
                {otherCrops.map((crop) => (
                  <option disabled={!crop.calculable} key={crop.cropName} value={crop.cropName}>
                    {crop.cropName}
                    {crop.calculable ? '' : ' — 계산 불가'}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          {otherStatus === 'loading' ? (
            <p className="mt-4 text-sm text-slate-500">선택한 작물로 다시 계산하는 중입니다.</p>
          ) : null}
          {otherStatus === 'error' ? (
            <p className="mt-4 text-sm font-semibold text-feedback-danger" role="alert">
              {selectedCropData?.blockedReason ?? '이 작물은 계산할 수 없습니다.'}
            </p>
          ) : null}

          {estimate ? (
            <>
              <div className="mt-5 grid gap-3 sm:grid-cols-3">
                <div className="rounded-app bg-leaf-50 p-3">
                  <p className="text-xs font-semibold text-slate-500">예상 월 매출</p>
                  <p className="mt-1 font-black text-ink-900">
                    {formatCurrency(estimate.monthlyRevenueKrw)}
                  </p>
                </div>
                <div className="rounded-app bg-soil-50 p-3">
                  <p className="text-xs font-semibold text-slate-500">예상 월 운영비</p>
                  <p className="mt-1 font-black text-ink-900">
                    {formatCurrency(estimate.monthlyOperatingCostKrw)}
                  </p>
                </div>
                <div className="rounded-app bg-skyfarm-50 p-3">
                  <p className="text-xs font-semibold text-slate-500">예상 월 영업이익</p>
                  <p
                    className={cn(
                      'mt-1 font-black',
                      profitTone(estimate.monthlyOperatingProfitKrw),
                    )}
                  >
                    {formatCurrency(estimate.monthlyOperatingProfitKrw)}
                  </p>
                </div>
              </div>

              <div className="mt-3 rounded-app border border-leaf-100 bg-white p-4">
                <p className="text-xs font-semibold text-slate-500">
                  공간 제공자 예상 배분수익 (영업이익의{' '}
                  {formatNumber(estimate.landlordShareRatio * 100)}%)
                </p>
                <p
                  className={cn(
                    'mt-1 text-2xl font-black',
                    profitTone(estimate.landlordExpectedIncomeKrw),
                  )}
                >
                  {formatCurrency(estimate.landlordExpectedIncomeKrw)}
                </p>
                <p className="mt-2 text-xs text-slate-500">
                  면적 활용률 {formatNumber(estimate.areaUtilizationPercent)}% · 다단{' '}
                  {formatNumber(estimate.moduleLayers)}층 · 월 판매량{' '}
                  {formatNumber(estimate.monthlySalesKg)}kg · kg당{' '}
                  {formatCurrency(estimate.pricePerKgKrw)}
                </p>
              </div>

              {/* 값의 성격을 밝힙니다. 조사값으로 바뀌면 이 문구도 함께 바뀝니다. */}
              <p className="mt-3 flex items-start gap-2 text-xs leading-5 text-slate-500">
                <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                <span>
                  {selectedCropData && !isEstimateData(selectedCropData.dataStatus)
                    ? `재배 파라미터는 조사값입니다(출처 ${selectedCropData.sourceId ?? '미표기'}).`
                    : '재배 파라미터와 설비 값(재배가능비율 0.6·다단 4층·천장고 2.5m)은 실측이 없어 추정값입니다.'}{' '}
                  금액은 서버 수익 계산기가 산출한 값입니다.
                </span>
              </p>
            </>
          ) : (
            <p className="mt-5 text-sm text-slate-600">
              이 작물은 재배 파라미터나 단가가 없어 금액을 계산할 수 없습니다.
            </p>
          )}

          <div className="mt-5 grid gap-2">
            {recommendedCrops.map((crop) => (
              <div
                key={crop.cropName}
                className="rounded-app border border-leaf-100 px-3 py-2"
              >
                <p className="font-bold text-ink-900">{crop.cropName}</p>
                <p className="mt-1 text-sm leading-6 text-slate-600">{crop.reason}</p>
              </div>
            ))}
          </div>

          {recommendation.cautions.length > 0 ? (
            <div className="mt-5 rounded-app border border-leaf-100 bg-white p-4">
              <h3 className="text-sm font-bold text-ink-900">운영 주의사항</h3>
              <ul className="mt-2 grid gap-1.5">
                {recommendation.cautions.map((caution) => (
                  <li className="text-sm leading-6 text-slate-600" key={caution}>
                    {caution}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      )}
    </Card>
  );
}
