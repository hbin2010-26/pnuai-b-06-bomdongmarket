package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.profit.dto.ProfitEstimateRequest;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateResponse;
import com.farmbroker.farmbroker.profit.service.ProfitEstimateService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 등록 전 수익 예측이 spaceId 없이 면적·월세만으로 동작하고,
// 공간 제공자 배분수익 내림차순으로 정렬되는지 검증한다.
class ProfitEstimateServiceTest {

    private static ProfitEstimateService service;

    // 단가는 이제 계산기가 아니라 MarketPriceProvider가 공급한다.
    // 운영 구현(SeedPriceProvider)은 DB를 읽으므로, 단위 테스트에서는 같은 값을 주는 스텁을 쓴다.
    // 값은 작물 백과사전 시드와 동일하다 — Python 기준값과의 동등성을 유지하기 위함.
    private static final LocalDate BASIS = LocalDate.of(2026, 7, 4);
    private static final Map<String, Integer> SEED_PRICES =
            Map.of("상추", 8000, "바질", 20000, "딸기", 30000);

    @BeforeAll
    static void setUp() {
        ProfitReferenceData data = new ProfitReferenceData();
        data.load();
        MarketPriceProvider prices = cropName ->
                Optional.ofNullable(SEED_PRICES.get(cropName))
                        .map(price -> MarketPrice.seed(price, BASIS));
        service = new ProfitEstimateService(new ProfitCalculator(data, data), prices);
    }

    private static ProfitEstimateRequest request(double area, int monthlyRent) {
        ProfitEstimateRequest request = new ProfitEstimateRequest();
        ReflectionTestUtils.setField(request, "area", BigDecimal.valueOf(area));
        ReflectionTestUtils.setField(request, "monthlyRent", monthlyRent);
        return request;
    }

    @Test
    void returns_every_supported_crop_sorted_by_landlord_income() {
        List<ProfitEstimateResponse> results = service.estimate(request(164, 1_200_000));

        assertEquals(List.of("상추", "딸기", "바질").size(), results.size());
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).landlordExpectedIncomeKrw()
                            >= results.get(i).landlordExpectedIncomeKrw(),
                    "배분수익 내림차순이어야 합니다");
        }
    }

    @Test
    void matches_python_reference_for_best_crop() {
        // ProfitCalculatorTest의 S001 기준값과 동일해야 한다 — 딸기가 배분수익 1위.
        ProfitEstimateResponse best = service.estimate(request(164, 1_200_000)).get(0);

        assertEquals("딸기", best.cropName());
        assertEquals(11_512_800L, best.monthlyRevenueKrw());
        assertEquals(2_973_302L, best.landlordExpectedIncomeKrw());
        assertEquals(1_200_000L, best.desiredMonthlyRentKrw());
        assertEquals("장기계약형", best.contractType());
    }

    @Test
    void carries_standard_assumptions_into_response() {
        ProfitEstimateResponse best = service.estimate(request(66, 500_000)).get(0);

        assertEquals(66.0, best.totalAreaM2());
        // 1.0.1 은 모든 공간에 재배가능비율 0.65 를 쓴다.
        assertEquals(65, best.areaUtilizationPercent());
        // 단수는 공간 가정값이 아니라 작물 속성이다 — 여기서 1위인 딸기는 2단.
        assertEquals(2.0, best.moduleLayers());
        assertEquals(2.5, best.ceilingHeightM());
    }

    // 추천 목록 밖의 작물을 골라 계산할 때 쓰는 경로다(#98).
    @Test
    void 작물을_지정하면_그_작물만_계산한다() {
        ProfitEstimateRequest request = request(66, 500_000);
        ReflectionTestUtils.setField(request, "cropNames", List.of("바질"));

        List<ProfitEstimateResponse> results = service.estimate(request);

        assertEquals(1, results.size());
        assertEquals("바질", results.get(0).cropName());
    }

    @Test
    void 모르는_작물만_지정하면_결과가_빈다() {
        ProfitEstimateRequest request = request(66, 500_000);
        ReflectionTestUtils.setField(request, "cropNames", List.of("없는작물"));

        assertTrue(service.estimate(request).isEmpty());
    }

    @Test
    void 작물을_지정하지_않으면_계산_가능한_전체를_돌려준다() {
        List<ProfitEstimateResponse> results = service.estimate(request(66, 500_000));

        assertEquals(SEED_PRICES.size(), results.size());
    }

    // 재배가능비율·층수·천장고는 설비 사양이라 표준 가정값이 임의값이다(#99).
    // 아는 값이 있으면 넣을 수 있어야 하고, 넣은 값이 계산에 반영돼야 한다.
    @Test
    void 설비_값을_주면_계산에_반영하고_응답에_그대로_싣는다() {
        ProfitEstimateRequest request = request(66, 500_000);
        ReflectionTestUtils.setField(request, "cropNames", List.of("상추"));
        ReflectionTestUtils.setField(request, "cultivableRatio", BigDecimal.valueOf(0.8));
        ReflectionTestUtils.setField(request, "ceilingHeightM", BigDecimal.valueOf(3.5));

        ProfitEstimateResponse custom = service.estimate(request).get(0);

        assertEquals(0.8, custom.cultivableRatio());
        assertEquals(80, custom.areaUtilizationPercent());
        assertEquals(3.5, custom.ceilingHeightM());

        ProfitEstimateRequest standard = request(66, 500_000);
        ReflectionTestUtils.setField(standard, "cropNames", List.of("상추"));
        ProfitEstimateResponse base = service.estimate(standard).get(0);

        // 재배면적이 늘면 생산량도 달라져야 한다 — 값만 응답에 실리고 계산은 그대로면 안 된다.
        assertNotEquals(base.cultivationAreaM2(), custom.cultivationAreaM2());
        assertNotEquals(base.monthlyRevenueKrw(), custom.monthlyRevenueKrw());
    }

    @Test
    void 설비_값을_비우면_표준_가정값을_쓴다() {
        ProfitEstimateResponse result = service.estimate(request(66, 500_000)).get(0);

        assertEquals(SpaceInputs.DEFAULT_CULTIVABLE_RATIO, result.cultivableRatio());
        assertEquals(SpaceInputs.DEFAULT_CEILING_HEIGHT_M, result.ceilingHeightM());
    }
}
