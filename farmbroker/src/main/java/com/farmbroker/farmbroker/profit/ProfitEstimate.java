package com.farmbroker.farmbroker.profit;

// 수익 계산기 결과 — 월평균(연간 시나리오 평균) 기준.
// basis(면적·다단·전력) 필드를 함께 담아 프론트가 "무엇을 근거로 계산했는지"를 보여줄 수 있게 한다.
// 모든 금액은 KRW/월, 에너지는 kWh/월, 면적은 ㎡.
public record ProfitEstimate(
        String cropName,

        // ── 계산 근거(가정 포함) ──
        double totalAreaM2,
        double cultivableRatio,        // 재배 가능 바닥 비율(가정)
        double moduleLayers,           // 다단 재배대 층 수 — 1.0.1 부터 작물마다 다르다
        double ceilingHeightM,         // 천장고(가정)
        double availableFloorAreaM2,   // = 전체면적 × 재배가능비율
        double cultivationAreaM2,      // = 사용가능바닥 × 작물별 다단 층수
        double lightingPowerW,         // 조명 정격 전력
        double averageMonthlyEnergyKwh,// 월평균 환경제어 전력량

        // ── 생산·매출 ──
        double monthlyTotalProductionKg,
        double monthlySalesKg,
        MarketPrice price,          // kg당 단가 + 기준일·출처(백과사전/KAMIS)
        double monthlyRevenueKrw,

        // ── 비용 ──
        double electricityCostKrw,
        double waterCostKrw,
        // 재료비는 모종비와 양액비로 나뉜다(1.0.1). 화면이 어느 쪽이 큰지 보여줄 수 있게 따로 담는다.
        double seedlingCostKrw,
        double nutrientSolutionL,
        double nutrientCostKrw,
        double materialCostKrw,     // = 모종비 + 양액비
        double laborCostKrw,
        // 설비는 사는 게 아니라 빌려 쓰는 것으로 잡는다 — 바닥면적 기준 월 대여비.
        double equipmentRentalCostKrw,
        double otherCostKrw,
        double monthlyOperatingCostKrw,

        // ── 손익·배분·계약 추천 ──
        double monthlyOperatingProfitKrw,
        double landlordShareRatio,
        double landlordExpectedIncomeKrw,
        double desiredMonthlyRentKrw,
        double rentIncomeDifferenceKrw,
        double businessOperatingProfitKrw,
        boolean operatingLoss,
        boolean longTermRecommended,
        String recommendation,
        String contractType) {
}
