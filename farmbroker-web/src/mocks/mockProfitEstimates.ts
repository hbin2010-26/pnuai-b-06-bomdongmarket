import type { ProfitCrop, ProfitEstimate, ProfitEstimateInput } from '@/types/api';

// 백엔드 ProfitCalculator(Python Profit_Calculator 1.0.1 포팅)가 66㎡ 기준으로 실제 산출한 값입니다.
// 손으로 적으면 틀려도 알아채기 어려워, 원본 파이프라인을 그대로 돌려 받은 숫자를 옮겼습니다.
//
// 대부분 항목이 재배면적(= 면적 × 재배가능비율 × 작물별 단수)에 비례합니다. 단수는 작물 속성이라
// 베이스라인에 이미 반영돼 있어, 목업은 면적과 재배가능비율만으로 환산합니다.
// 이미 반올림된 값에서 손익을 다시 계산하므로 서버 응답과 원 단위로 1원까지 다를 수 있습니다.
const BASE_AREA_M2 = 66;
const BASE_CULTIVABLE_RATIO = 0.65;

interface ProfitBaseline {
  cropName: string;
  moduleLayers: number;
  pricePerKgKrw: number;
  lightingPowerW: number;
  averageMonthlyEnergyKwh: number;
  monthlyTotalProductionKg: number;
  monthlySalesKg: number;
  monthlyRevenueKrw: number;
  electricityCostKrw: number;
  waterCostKrw: number;
  seedlingCostKrw: number;
  nutrientSolutionL: number;
  nutrientCostKrw: number;
  laborCostKrw: number;
}

const baselines: ProfitBaseline[] = [
  {
    cropName: '상추',
    moduleLayers: 4,
    pricePerKgKrw: 8000,
    lightingPowerW: 12257,
    averageMonthlyEnergyKwh: 14408,
    monthlyTotalProductionKg: 515,
    monthlySalesKg: 463,
    monthlyRevenueKrw: 3706560,
    electricityCostKrw: 2233257,
    waterCostKrw: 55803,
    seedlingCostKrw: 858000,
    nutrientSolutionL: 18121,
    nutrientCostKrw: 362419,
    laborCostKrw: 2656368,
  },
  {
    cropName: '딸기',
    moduleLayers: 2,
    pricePerKgKrw: 30000,
    lightingPowerW: 7661,
    averageMonthlyEnergyKwh: 5205,
    monthlyTotalProductionKg: 172,
    monthlySalesKg: 154,
    monthlyRevenueKrw: 4633200,
    electricityCostKrw: 806810,
    waterCostKrw: 8641,
    seedlingCostKrw: 321750,
    nutrientSolutionL: 2548,
    nutrientCostKrw: 50965,
    laborCostKrw: 885456,
  },
  {
    cropName: '바질',
    moduleLayers: 3,
    pricePerKgKrw: 25000,
    lightingPowerW: 11491,
    averageMonthlyEnergyKwh: 8187,
    monthlyTotalProductionKg: 309,
    monthlySalesKg: 278,
    monthlyRevenueKrw: 6949800,
    electricityCostKrw: 1268911,
    waterCostKrw: 15072,
    seedlingCostKrw: 643500,
    nutrientSolutionL: 4672,
    nutrientCostKrw: 93436,
    laborCostKrw: 1593821,
  },
  {
    cropName: '애플민트',
    moduleLayers: 4,
    pricePerKgKrw: 60000,
    lightingPowerW: 10419,
    averageMonthlyEnergyKwh: 9869,
    monthlyTotalProductionKg: 233,
    monthlySalesKg: 210,
    monthlyRevenueKrw: 12602304,
    electricityCostKrw: 1529739,
    waterCostKrw: 38653,
    seedlingCostKrw: 343200,
    nutrientSolutionL: 12458,
    nutrientCostKrw: 249163,
    laborCostKrw: 1204220,
  },
  {
    cropName: '쪽파',
    moduleLayers: 3,
    pricePerKgKrw: 10000,
    lightingPowerW: 9193,
    averageMonthlyEnergyKwh: 10718,
    monthlyTotalProductionKg: 257,
    monthlySalesKg: 232,
    monthlyRevenueKrw: 2316600,
    electricityCostKrw: 1661305,
    waterCostKrw: 39510,
    seedlingCostKrw: 90090,
    nutrientSolutionL: 12741,
    nutrientCostKrw: 254826,
    laborCostKrw: 1328184,
  },
  {
    cropName: '병풀',
    moduleLayers: 3,
    pricePerKgKrw: 20000,
    lightingPowerW: 9193,
    averageMonthlyEnergyKwh: 9514,
    monthlyTotalProductionKg: 257,
    monthlySalesKg: 232,
    monthlyRevenueKrw: 4633200,
    electricityCostKrw: 1474692,
    waterCostKrw: 39510,
    seedlingCostKrw: 214500,
    nutrientSolutionL: 12741,
    nutrientCostKrw: 254826,
    laborCostKrw: 1328184,
  },
];

// 백엔드 standard_info.csv·SpaceInputs와 같은 값으로 유지합니다.
const CEILING_HEIGHT_M = 2.5;
const LANDLORD_SHARE_RATIO = 0.8;
const OTHER_COST_KRW = 300000;
const EQUIPMENT_RENTAL_KRW_M2 = 22000;
// 백과사전 기준 단가의 기준일 — 서버 SeedPriceProvider와 같은 값입니다.
const SEED_BASIS_DATE = '2026-07-04';

function toEstimate(
  baseline: ProfitBaseline,
  { area, monthlyRent, cultivableRatio, ceilingHeightM }: ProfitEstimateInput,
): ProfitEstimate {
  const ratio = cultivableRatio ?? BASE_CULTIVABLE_RATIO;
  const ceiling = ceilingHeightM ?? CEILING_HEIGHT_M;
  const availableFloorArea = area * ratio;
  // 재배면적에 비례하는 항목을 한 번에 환산합니다. 단수는 작물 속성이라 베이스라인에 들어 있습니다.
  const scale = availableFloorArea / (BASE_AREA_M2 * BASE_CULTIVABLE_RATIO);
  const round = (value: number) => Math.round(value * scale);

  const monthlyRevenueKrw = round(baseline.monthlyRevenueKrw);
  const electricityCostKrw = round(baseline.electricityCostKrw);
  const waterCostKrw = round(baseline.waterCostKrw);
  const seedlingCostKrw = round(baseline.seedlingCostKrw);
  const nutrientCostKrw = round(baseline.nutrientCostKrw);
  const materialCostKrw = seedlingCostKrw + nutrientCostKrw;
  const laborCostKrw = round(baseline.laborCostKrw);
  // 기기 대여비는 바닥면적 기준이라 단수와 무관합니다.
  const equipmentRentalCostKrw = Math.round(availableFloorArea * EQUIPMENT_RENTAL_KRW_M2);
  // 기타비용은 면적과 무관한 고정비입니다.
  const monthlyOperatingCostKrw =
    electricityCostKrw +
    waterCostKrw +
    materialCostKrw +
    equipmentRentalCostKrw +
    laborCostKrw +
    OTHER_COST_KRW;
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
    moduleLayers: baseline.moduleLayers,
    ceilingHeightM: ceiling,
    availableFloorAreaM2: Math.round(availableFloorArea * 10) / 10,
    cultivationAreaM2: Math.round(availableFloorArea * baseline.moduleLayers * 10) / 10,
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
    seedlingCostKrw,
    nutrientSolutionL: round(baseline.nutrientSolutionL),
    nutrientCostKrw,
    materialCostKrw,
    laborCostKrw,
    equipmentRentalCostKrw,
    otherCostKrw: OTHER_COST_KRW,
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

export function createMockProfitEstimates(input: ProfitEstimateInput): ProfitEstimate[] {
  // 서버와 같이 cropNames로 좁힐 수 있어야 합니다 — 추천 밖 작물 계산이 이 경로를 씁니다.
  const wanted = input.cropNames?.length ? new Set(input.cropNames) : null;
  return baselines
    .filter((baseline) => (wanted ? wanted.has(baseline.cropName) : true))
    .map((baseline) => toEstimate(baseline, input))
    .sort((a, b) => b.landlordExpectedIncomeKrw - a.landlordExpectedIncomeKrw);
}

// GET /profit/crops 목업입니다. 계산 가능한 작물에, 재배 파라미터는 들어왔지만 단가가 없어
// 계산이 막힌 작물 하나를 함께 둡니다 — 화면이 그 경우를 어떻게 보여 주는지 확인하기 위함입니다.
// 정렬은 서버와 같은 가나다순입니다.
const SEED_REMARKS =
  'Profit_Calculator 1.0.1 crop_production_info.csv 에서 이관한 값 — 작물별 자료 조사로 보완 필요';

export const mockProfitCrops: ProfitCrop[] = [
  {
    cropName: '딸기',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 30000,
    priceSource: 'SEED',
  },
  {
    cropName: '무순',
    calculable: false,
    blockedReason: '단가 정보가 없습니다. 작물 백과사전에 kg당 단가를 넣어야 계산됩니다.',
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
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
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 25000,
    priceSource: 'SEED',
  },
  {
    cropName: '병풀',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 20000,
    priceSource: 'SEED',
  },
  {
    cropName: '상추',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 8000,
    priceSource: 'SEED',
  },
  {
    cropName: '애플민트',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 60000,
    priceSource: 'SEED',
  },
  {
    cropName: '쪽파',
    calculable: true,
    blockedReason: null,
    dataStatus: 'MVP_ESTIMATE',
    sourceId: 'PROFIT_CALCULATOR_CSV_1_0_1',
    referenceDate: '2026-07-04',
    remarks: SEED_REMARKS,
    pricePerKgKrw: 10000,
    priceSource: 'SEED',
  },
];
