package com.farmbroker.farmbroker.profit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 자바 포팅이 Python Profit_Calculator 1.0.1과 수치적으로 일치하는지 검증한다.
// 기댓값은 Python calculate_all_sites()의 S001-상추 / S001-딸기 시나리오를 그대로 돌려 받은 값이다
// (손으로 옮겨 적으면 숫자가 틀려도 알아채기 어려워 스크립트로 뽑았다).
// S001 입력(면적 164, 재배가능 0.65, 천장 2.5, 월세 1,200,000)은 SpaceInputs 기본 가정값과 동일하다.
// 다단 층 수는 1.0.1부터 작물 속성이라 공간 입력에 없다 — 상추 4단, 딸기 2단.
class ProfitCalculatorTest {

    private static ProfitCalculator calculator;

    // Python 원본 crop_sale_info.csv와 동일한 값. 단가 소스와 무관하게 계산 동등성을 고정하기 위해
    // 테스트에서 직접 주입한다(운영 경로에서는 MarketPriceProvider가 넣어 준다).
    private static final LocalDate BASIS = LocalDate.of(2026, 7, 4);
    private static final MarketPrice LETTUCE_PRICE = MarketPrice.seed(8000, BASIS);
    private static final MarketPrice STRAWBERRY_PRICE = MarketPrice.seed(30000, BASIS);

    @BeforeAll
    static void setUp() {
        ProfitReferenceData data = new ProfitReferenceData();
        data.load();
        // CSV 구현을 그대로 파라미터 공급자로 쓴다 — DB 없이 Python 원본과의 수치 일치를 확인한다.
        calculator = new ProfitCalculator(data, data);
    }

    private static void assertClose(double expected, double actual) {
        // 금액·에너지는 KRW/kWh 단위로 1e-3 안쪽이면 충분(Python double과 동일 공식)
        assertEquals(expected, actual, Math.max(1e-3, Math.abs(expected) * 1e-9),
                "expected " + expected + " but got " + actual);
    }

    @Test
    void lettuce_matches_python_reference() {
        ProfitEstimate e = calculator.estimate(SpaceInputs.fromSpace(164, 1_200_000), "상추", LETTUCE_PRICE);

        assertClose(4.0, e.moduleLayers());
        assertClose(106.60000000000001, e.availableFloorAreaM2());
        assertClose(426.40000000000003, e.cultivationAreaM2());
        assertClose(1279.2, e.monthlyTotalProductionKg());
        assertClose(1151.28, e.monthlySalesKg());
        assertClose(9_210_240.0, e.monthlyRevenueKrw());
        assertClose(30457.14285714286, e.lightingPowerW());
        assertClose(35787.46703603096, e.averageMonthlyEnergyKwh());
        assertClose(5_547_057.3905847985, e.electricityCostKrw());
        assertClose(138_661.4142857143, e.waterCostKrw());
        assertClose(2_132_000.0, e.seedlingCostKrw());
        assertClose(59_289.90476190478, e.nutrientSolutionL());
        assertClose(1_185_798.0952380956, e.nutrientCostKrw());
        assertClose(3_317_798.0952380956, e.materialCostKrw());
        assertClose(6_600_672.0, e.laborCostKrw());
        assertClose(2_345_200.0, e.equipmentRentalCostKrw());
        assertClose(300_000.0, e.otherCostKrw());
        assertClose(18_249_388.90010861, e.monthlyOperatingCostKrw());
        assertClose(-9_039_148.90010861, e.monthlyOperatingProfitKrw());
        assertClose(-7_231_319.120086888, e.landlordExpectedIncomeKrw());
        assertClose(-1_807_829.7800217215, e.businessOperatingProfitKrw());
        assertTrue(e.operatingLoss());
        assertFalse(e.longTermRecommended());
        assertEquals("단기계약형", e.contractType());
    }

    // 딸기는 2단이라 상추와 재배면적이 다르다 — 단수가 작물 속성으로 옮겨진 것이 여기서 드러난다.
    @Test
    void strawberry_matches_python_reference() {
        ProfitEstimate e = calculator.estimate(SpaceInputs.fromSpace(164, 1_200_000), "딸기", STRAWBERRY_PRICE);

        assertClose(2.0, e.moduleLayers());
        assertClose(213.20000000000002, e.cultivationAreaM2());
        assertClose(11_512_800.000000002, e.monthlyRevenueKrw());
        assertClose(12923.461008791195, e.averageMonthlyEnergyKwh());
        assertClose(2_003_136.4563626351, e.electricityCostKrw());
        assertClose(21_471.211904761905, e.waterCostKrw());
        assertClose(8_337.642857142859, e.nutrientSolutionL());
        assertClose(966_252.8571428573, e.materialCostKrw());
        assertClose(2_345_200.0, e.equipmentRentalCostKrw());
        assertClose(7_836_284.5254102545, e.monthlyOperatingCostKrw());
        assertClose(3_676_515.4745897474, e.monthlyOperatingProfitKrw());
        assertClose(2_941_212.379671798, e.landlordExpectedIncomeKrw());
        assertTrue(e.longTermRecommended());
        assertEquals("장기계약형", e.contractType());
    }
}
