import type { ProfitEstimate } from '@/types/api';
import { formatCurrency } from '@/utils/format';

// 운영비가 어디에 얼마나 쓰이는지 보여주는 도넛 차트입니다.
//
// 차트 라이브러리를 새로 넣지 않고 SVG 로 그립니다. 이 화면에 필요한 건 원 하나라
// recharts(gzip 100KB 남짓)를 통째로 들이는 것보다 이쪽이 가볍고, 색·글꼴도
// 나머지 화면과 같은 토큰을 그대로 씁니다.
//
// 도넛은 눈으로 비중을 보는 용도이고, 실제 숫자는 아래 범례가 책임집니다 —
// 그래야 스크린리더와 테스트도 금액을 읽을 수 있습니다.

interface CostBreakdownChartProps {
  estimate: ProfitEstimate;
}

// 비용 항목과 색입니다. 순서가 곧 도넛을 도는 순서라, 큰 항목부터 두지 않고
// 계산기 블록 순서(전기 → 용수 → 재료 → 인건 → 설비 → 기타)를 따릅니다.
// 같은 작물을 다시 계산해도 조각 위치가 그대로여야 비교가 됩니다.
const COST_ITEMS = [
  { key: 'electricityCostKrw', label: '전기비', color: '#f59e0b' },
  { key: 'waterCostKrw', label: '수도비', color: '#0ea5e9' },
  { key: 'seedlingCostKrw', label: '모종비', color: '#22c55e' },
  { key: 'nutrientCostKrw', label: '양액비', color: '#14b8a6' },
  { key: 'laborCostKrw', label: '인건비', color: '#8b5cf6' },
  { key: 'equipmentRentalCostKrw', label: '기기 대여비', color: '#64748b' },
  { key: 'otherCostKrw', label: '기타비용', color: '#a1a1aa' },
] as const satisfies ReadonlyArray<{
  key: keyof ProfitEstimate;
  label: string;
  color: string;
}>;

const RADIUS = 60;
const STROKE = 26;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

export function CostBreakdownChart({ estimate }: CostBreakdownChartProps) {
  const slices = COST_ITEMS.map((item) => ({
    ...item,
    amount: Math.max(0, Number(estimate[item.key]) || 0),
  })).filter((slice) => slice.amount > 0);

  // 합계는 항목을 더해서 냅니다. monthlyOperatingCostKrw 를 쓰면 반올림 때문에
  // 조각의 합이 100%가 아닌 값이 되어 도넛에 빈틈이 생깁니다.
  const total = slices.reduce((sum, slice) => sum + slice.amount, 0);
  if (total <= 0) return null;

  let offset = 0;
  const arcs = slices.map((slice) => {
    const ratio = slice.amount / total;
    const arc = { ...slice, ratio, dash: ratio * CIRCUMFERENCE, offset };
    offset += arc.dash;
    return arc;
  });

  return (
    <div className="mt-3 rounded-app border border-leaf-100 bg-white p-4">
      <h3 className="text-sm font-bold text-ink-900">월 운영비 구성</h3>
      <p className="mt-1 text-xs text-slate-500">
        합계 {formatCurrency(total)} · 비중이 큰 항목부터 줄여야 손익이 바뀝니다.
      </p>

      <div className="mt-4 flex flex-col items-center gap-5 sm:flex-row sm:items-center">
        <svg
          aria-label={`월 운영비 구성. ${arcs
            .map((arc) => `${arc.label} ${Math.round(arc.ratio * 100)}퍼센트`)
            .join(', ')}`}
          className="shrink-0"
          height={160}
          role="img"
          viewBox="0 0 160 160"
          width={160}
        >
          {/* -90도 회전으로 12시 방향에서 시작합니다. */}
          <g transform="rotate(-90 80 80)">
            {arcs.map((arc) => (
              <circle
                cx={80}
                cy={80}
                fill="none"
                key={arc.key}
                r={RADIUS}
                stroke={arc.color}
                strokeDasharray={`${arc.dash} ${CIRCUMFERENCE - arc.dash}`}
                strokeDashoffset={-arc.offset}
                strokeWidth={STROKE}
              />
            ))}
          </g>
        </svg>

        <ul className="grid w-full gap-1.5">
          {arcs.map((arc) => (
            <li className="flex items-center gap-2 text-sm" key={arc.key}>
              <span
                aria-hidden
                className="h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: arc.color }}
              />
              <span className="font-medium text-slate-600">{arc.label}</span>
              <span className="ml-auto font-bold text-ink-900">
                {formatCurrency(arc.amount)}
              </span>
              <span className="w-12 text-right tabular-nums text-slate-500">
                {(arc.ratio * 100).toFixed(1)}%
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
