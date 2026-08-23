package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.dto.ProfitCropResponse;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import com.farmbroker.farmbroker.profit.service.ProfitCropCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 작물 선택 목록. 재배 파라미터가 전부 추정값인 단계라 숫자만 보여 주면 실측처럼 읽힌다(#99).
// 값의 출처를 함께 내리고, 계산이 막힌 작물은 그 이유까지 보여야 담당자가 무엇을 채울지 안다.
class ProfitCropCatalogServiceTest {

    private static final LocalDate BASIS = LocalDate.of(2026, 7, 4);

    private ProfitCropCatalogService service(List<CropCultivationParam> rows, Map<String, Integer> prices) {
        CropCultivationParamRepository repository = mock(CropCultivationParamRepository.class);
        when(repository.findAllByOrderByCropNameAsc()).thenReturn(rows);
        MarketPriceProvider priceProvider = cropName -> Optional.ofNullable(prices.get(cropName))
                .map(price -> MarketPrice.seed(price, BASIS));
        return new ProfitCropCatalogService(repository, priceProvider);
    }

    @Test
    @DisplayName("파라미터와 단가가 모두 있으면 계산 가능으로 내린다")
    void marksCalculableWhenPriceExists() {
        ProfitCropCatalogService service = service(
                List.of(row("상추", "MVP_ESTIMATE", "PROFIT_CALCULATOR_CSV_0_3_1")),
                Map.of("상추", 8000));

        ProfitCropResponse crop = service.crops().get(0);

        assertThat(crop.cropName()).isEqualTo("상추");
        assertThat(crop.calculable()).isTrue();
        assertThat(crop.blockedReason()).isNull();
        assertThat(crop.pricePerKgKrw()).isEqualTo(8000L);
        assertThat(crop.priceSource()).isEqualTo("SEED");
    }

    // 파라미터는 들어왔는데 단가가 없으면 계산 목록에서 조용히 빠진다.
    // 목록에서 빼 버리면 담당자가 왜 안 나오는지 알 수 없어, 이유를 붙여 그대로 내린다.
    @Test
    @DisplayName("단가가 없으면 계산 불가로 내리되 목록에서 빼지 않는다")
    void keepsCropWithoutPriceAndExplainsWhy() {
        ProfitCropCatalogService service = service(
                List.of(row("무순", "MVP_ESTIMATE", "PROFIT_CALCULATOR_CSV_0_3_1")),
                Map.of());

        ProfitCropResponse crop = service.crops().get(0);

        assertThat(crop.cropName()).isEqualTo("무순");
        assertThat(crop.calculable()).isFalse();
        assertThat(crop.blockedReason()).contains("단가");
        assertThat(crop.pricePerKgKrw()).isNull();
    }

    @Test
    @DisplayName("값의 출처와 성격을 함께 내린다")
    void exposesProvenance() {
        ProfitCropCatalogService service = service(
                List.of(row("시금치", "RESEARCHED", "RESEARCH_2026_08")),
                Map.of("시금치", 6000));

        ProfitCropResponse crop = service.crops().get(0);

        assertThat(crop.dataStatus()).isEqualTo("RESEARCHED");
        assertThat(crop.sourceId()).isEqualTo("RESEARCH_2026_08");
        assertThat(crop.referenceDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(crop.remarks()).isEqualTo("출처 표기 확인용");
    }

    private CropCultivationParam row(String cropName, String dataStatus, String sourceId) {
        return CropCultivationParam.builder()
                .cropName(cropName)
                .moduleLayers(4.0)
                .yieldPerCycleKgM2(3.0)
                .cyclesPerMonth(1.0)
                .marketableRate(0.9)
                .requiredPpfdUmolM2S(200.0)
                .lightingHoursDay(16.0)
                .targetTemperatureC(20.0)
                .targetRelativeHumidity(0.7)
                .dailyEvapotranspirationMm(3.0)
                .seedlingCostPerM2MonthKrw(1000.0)
                .sourceId(sourceId)
                .dataStatus(dataStatus)
                .referenceDate(LocalDate.of(2026, 8, 1))
                .remarks("출처 표기 확인용")
                .build();
    }
}
