import { SlidersHorizontal } from 'lucide-react';

import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { formatNumber } from '@/utils/format';

// 서버 SpaceInputs 의 표준 가정값·허용 범위와 같은 값입니다. 어긋나면 화면에서 넣은 값이
// 서버 검증에 걸립니다.
export const FACILITY_DEFAULTS = {
  cultivableRatio: 0.6,
  moduleLayers: 4,
  ceilingHeightM: 2.5,
} as const;

const LIMITS = {
  cultivableRatio: { min: 0.1, max: 1, step: 0.05 },
  moduleLayers: { min: 1, max: 10, step: 1 },
  ceilingHeightM: { min: 1.5, max: 10, step: 0.1 },
} as const;

export interface FacilityAssumptions {
  cultivableRatio: number;
  moduleLayers: number;
  ceilingHeightM: number;
}

interface FacilityAssumptionCardProps {
  value: FacilityAssumptions;
  onChange: (next: FacilityAssumptions) => void;
  disabled?: boolean;
}

// 재배가능비율·다단 층수·천장고를 직접 조절하는 카드입니다.
//
// 이 세 값은 작물 특성이 아니라 설비 사양이라 실측 없이는 정할 수 없고, 지금까지 코드에
// 박힌 임의값이었습니다(#99). 값을 아는 사람이 넣을 수 있게 열어 두고, 표준 가정값을
// 그대로 쓰고 있는지 화면에 밝힙니다.
export function FacilityAssumptionCard({
  value,
  onChange,
  disabled = false,
}: FacilityAssumptionCardProps) {
  const isDefault =
    value.cultivableRatio === FACILITY_DEFAULTS.cultivableRatio &&
    value.moduleLayers === FACILITY_DEFAULTS.moduleLayers &&
    value.ceilingHeightM === FACILITY_DEFAULTS.ceilingHeightM;

  return (
    <Card padding="lg">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-black text-content">
            <SlidersHorizontal className="h-5 w-5 text-accent" aria-hidden />
            설비 조건
          </h2>
          <p className="mt-2 max-w-xl text-body-sm text-content-muted">
            {isDefault
              ? '아래 값은 실측이 없어 쓰는 표준 가정값입니다. 도입할 설비 사양을 알고 있으면 바꿔 보세요 — 예상 수익이 함께 다시 계산됩니다.'
              : '입력한 설비 조건으로 계산합니다. 표준 가정값과 다른 값이라 다른 공간의 예측과 바로 비교하기는 어렵습니다.'}
          </p>
        </div>
        {!isDefault ? (
          <Button
            disabled={disabled}
            onClick={() => onChange({ ...FACILITY_DEFAULTS })}
            size="sm"
            variant="outline"
          >
            표준 가정값으로
          </Button>
        ) : null}
      </div>

      <div className="mt-5 grid gap-4 sm:grid-cols-3">
        <label className="grid gap-1">
          <span className="text-xs font-semibold text-content-subtle">
            재배 가능 바닥 비율
          </span>
          <input
            aria-label="재배 가능 바닥 비율"
            className="min-h-control rounded-app border border-line px-3 text-base font-bold text-content"
            disabled={disabled}
            max={LIMITS.cultivableRatio.max}
            min={LIMITS.cultivableRatio.min}
            onChange={(event) =>
              onChange({ ...value, cultivableRatio: Number(event.target.value) })
            }
            step={LIMITS.cultivableRatio.step}
            type="number"
            value={value.cultivableRatio}
          />
          <span className="text-xs text-content-subtle">
            통로·설비를 뺀 비율 · 현재 {formatNumber(Math.round(value.cultivableRatio * 100))}%
          </span>
        </label>

        <label className="grid gap-1">
          <span className="text-xs font-semibold text-content-subtle">다단 재배대 층 수</span>
          <input
            aria-label="다단 재배대 층 수"
            className="min-h-control rounded-app border border-line px-3 text-base font-bold text-content"
            disabled={disabled}
            max={LIMITS.moduleLayers.max}
            min={LIMITS.moduleLayers.min}
            onChange={(event) =>
              onChange({ ...value, moduleLayers: Number(event.target.value) })
            }
            step={LIMITS.moduleLayers.step}
            type="number"
            value={value.moduleLayers}
          />
          <span className="text-xs text-content-subtle">
            1~{LIMITS.moduleLayers.max}층 · 층이 늘면 재배면적도 늘어납니다
          </span>
        </label>

        <label className="grid gap-1">
          <span className="text-xs font-semibold text-content-subtle">천장고 (m)</span>
          <input
            aria-label="천장고"
            className="min-h-control rounded-app border border-line px-3 text-base font-bold text-content"
            disabled={disabled}
            max={LIMITS.ceilingHeightM.max}
            min={LIMITS.ceilingHeightM.min}
            onChange={(event) =>
              onChange({ ...value, ceilingHeightM: Number(event.target.value) })
            }
            step={LIMITS.ceilingHeightM.step}
            type="number"
            value={value.ceilingHeightM}
          />
          <span className="text-xs text-content-subtle">
            다단을 몇 층까지 올릴 수 있는지를 좌우합니다
          </span>
        </label>
      </div>
    </Card>
  );
}
