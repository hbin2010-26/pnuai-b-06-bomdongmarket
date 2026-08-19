import type { ProfitCrop, ProfitEstimate, ProfitEstimateInput } from '@/types/api';

// 백엔드 ProfitCalculator가 66㎡ 기준으로 실제 산출한 값입니다.
// 계산기의 대부분 항목은 재배면적(= 면적 × 0.6 × 4단)에 비례하므로 mock은 면적 비율로 환산합니다.
// 이미 반올림된 값에서 손익을 다시 계산하므로 서버 응답과 원 단위로 1원까지 다를 수 있습니다.
const BASE_AREA_M2 = 66;

interface ProfitBaseline {
  cropName: string;
  lightingPowerW: number;
  averageMonthlyEnergyKwh: number;
  monthlyTotalProductionKg: number;
  monthlySalesKg: number;
  pricePerKgKrw: number;
  monthlyRevenueKrw: number;
  electricityCostKrw: number;
  waterCostKrw: number;
  materialCostKrw: number;
  laborCostKrw: number;
}

const baselines: ProfitBaseline[] = [
  {
    cropName: '상추',
    lightingPowerW: 11314,
    averageMonthlyEnergyKwh: 9204,
    monthlyTotalProductionKg: 475,
    monthlySalesKg: 428,
    pricePerKgKrw: 8000,
    monthlyRevenueKrw: 3421440,
    electricityCostKrw: 1426612,
    waterCostKrw: 5721,
    materialCostKrw: 1488000,
    laborCostKrw: 2452032,
  },
  {
    cropName: '딸기',
    lightingPowerW: 14143,
    averageMonthlyEnergyKwh: 16606,
    monthlyTotalProductionKg: 246,
    monthlySalesKg: 221,
    pricePerKgKrw: 30000,
    monthlyRevenueKrw: 6629040,
    electricityCostKrw: 2573894,
    waterCostKrw: 14032,
    materialCostKrw: 1191000,
    laborCostKrw: 1266883,
  },
  {
    cropName: '바질',
    lightingPowerW: 14143,
    averageMonthlyEnergyKwh: 15864,
    monthlyTotalProductionKg: 380,
    monthlySalesKg: 342,
    pricePerKgKrw: 20000,
    monthlyRevenueKrw: 6842880,
    electricityCostKrw: 2458977,
    waterCostKrw: 12948,
    materialCostKrw: 2200800,
    laborCostKrw: 1961626,
  },
];

// 백엔드 SpaceInputs의 표준 가정값과 동일하게 유지합니다.
const CULTIVABLE_RATIO = 0.6;
const MODULE_LAYERS = 4;
const CEILING_HEIGHT_M = 2.5;
const LANDLORD_SHARE_RATIO = 0.8;
const DEPRECIATION_AND_OTHER_COST_KRW = 100000;
// 백과사전 기준 단가의 기준일 — 서버 SeedPriceProvider와 같은 값입니다.
const SEED_BASIS_DATE = '2026-07-04';

function toEstimate(
  baseline: ProfitBaseline,
  { area, monthlyRent, cultivableRatio, moduleLayers, ceilingHeightM }: ProfitEstimateInput,
): ProfitEstimate {
  const ratio = cultivableRatio ?? CULTIVABLE_RATIO;
  const layers = moduleLayers ?? MODULE_LAYERS;
  const ceiling = ceilingHeightM ?? CEILING_HEIGHT_M;
  // 계산기 대부분 항목이 재배면적에 비례하므로, 설비 값을 바꾸면 금액도 함께 움직여야 합니다.
  // 그러지 않으면 조절 UI를 만들어도 목업에서는 숫자가 그대로여서 동작을 확인할 수 없습니다.
  const scale =
    (area * ratio * layers) / (BASE_AREA_M2 * CULTIVABLE_RATIO * MODULE_LAYERS);
  const round = (value: number) => Math.round(value * scale);

  const monthlyRevenueKrw = round(baseline.monthlyRevenueKrw);
  const electricityCostKrw = round(baseline.electricityCostKrw);
  const waterCostKrw = round(baseline.waterCostKrw);
  const materialCostKrw = round(baseline.materialCostKrw);
  const laborCostKrw = round(baseline.laborCostKrw);
  // 감가상각 등 기타비는 면적과 무관한 고정비입니다.
  const monthlyOperatingCostKrw =
    electricityCostKrw +
    waterCostKrw +
    materialCostKrw +
    laborCostKrw +
    DEPRECIATION_AND_OTHER_COST_KRW;
  const monthlyOperatingProfitKrw = monthlyRevenueKrw - monthlyOperatingCostKrw;
  const landlordExpectedIncomeKrw = Math.round(
    monthlyOperatingProfitKrw * LANDLORD_SHARE_RATIO,
  );
  const operatingLoss = monthlyOperatingProfitKrw < 0;
  const longTermRecommended = !operatingLoss && landlordExpectedIncomeKrw >= monthlyRent;

  return {
    cropName: baseline.cropName,
    totalAreaM2: area,
    cultivableRatio: ratio,
    areaUtilizationPercent: Math.round(ratio * 100),
    moduleLayers: layers,
    ceilingHeightM: ceiling,
    availableFloorAreaM2: Math.round(area * ratio * 10) / 10,
    cultivationAreaM2: Math.round(area * ratio * layers * 10) / 10,
    lightingPowerW: round(baseline.lightingPowerW),
    averageMonthlyEnergyKwh: round(baseline.averageMonthlyEnergyKwh),
    monthlyTotalProductionKg: round(baseline.monthlyTotalProductionKg),
    monthlySalesKg: round(baseline.monthlySalesKg),
    pricePerKgKrw: baseline.pricePerKgKrw,
    priceSource: 'SEED',
    priceBasisDate: SEED_BASIS_DATE,
    monthlyRevenueKrw,
    electricityCostKrw,
    waterCostKrw,
    materialCostKrw,
    laborCostKrw,
    depreciationAndOtherCostKrw: DEPRECIATION_AND_OTHER_COST_KRW,
    monthlyOperatingCostKrw,
    monthlyOperatingProfitKrw,
    landlordShareRatio: LANDLORD_SHARE_RATIO,
    landlordExpectedIncomeKrw,
    desiredMonthlyRentKrw: monthlyRent,
    businessOperatingProfitKrw: monthlyOperatingProfitKrw - landlordExpectedIncomeKrw,
    operatingLoss,
    longTermRecommended,
    recommendation: longTermRecommended
      ? '도심형 대량생산 스마트팜 방식 추천'
      : '개인취미 대여 방식 추천',
    contractType: longTermRecommended ? '장기계약형' : '단기계약형',
  };
}

// 백엔드와 동일하게 공간 제공자 배분수익이 큰 순서로 정렬해 첫 항목이 대표 작물이 되게 합니다.
export function createMockProfitEstimates(input: ProfitEstimateInput): ProfitEstimate[] {
  // 서버와 같이 cropNames로 좁힐 수 있어야 합니다 — 추천 밖 작물 계산이 이 경로를 씁니다.
  const wanted = input.cropNames?.length ? new Set(input.cropNames) : null;
  return baselines
    .filter((baseline) => (wanted ? wanted.has(baseline.cropName) : true))
    .map((baseline) => toEstimate(baseline, input))
    .sort((a, b) => b.landlordExpectedIncomeKrw - a.landlordExpectedIncomeKrw);
}

// GET /profit/crops 목업입니다. 계산 가능한 3종에, 재배 파라미터는 들어왔지만 단가가 없어
// 계산이 막힌 작물 하나를 함께 둡니다 — 화면이 그 경우를 어떻게 보여 주는지 확인하기 위함입니다.
// 정렬은 서버와 같은 가나다순입니다.
export const mockProfitCrops: ProfitCrop[] = [
  {
    cropName: '딸기',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_0_3_1',
    referenceDate: '2026-07-04',
    remarks: 'crop_production_info.csv 에서 이관한 추정값 — 작물별 자료 조사로 보완 필요',
    pricePerKgKrw: 30000,
    priceSource: 'SEED',
  },
  {
    cropName: '무순',
    calculable: false,
    blockedReason: '단가 정보가 없습니다. 작물 백과사전에 kg당 단가를 넣어야 계산됩니다.',
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_0_3_1',
    referenceDate: '2026-07-04',
    remarks: '새싹채소 대표가 없어 잎채소를 차용한 값',
    pricePerKgKrw: null,
    priceSource: null,
  },
  {
    cropName: '바질',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_0_3_1',
    referenceDate: '2026-07-04',
    remarks: 'crop_production_info.csv 에서 이관한 추정값 — 작물별 자료 조사로 보완 필요',
    pricePerKgKrw: 20000,
    priceSource: 'SEED',
  },
  {
    cropName: '상추',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_0_3_1',
    referenceDate: '2026-07-04',
    remarks: 'crop_production_info.csv 에서 이관한 추정값 — 작물별 자료 조사로 보완 필요',
    pricePerKgKrw: 8000,
    priceSource: 'SEED',
  },
];
