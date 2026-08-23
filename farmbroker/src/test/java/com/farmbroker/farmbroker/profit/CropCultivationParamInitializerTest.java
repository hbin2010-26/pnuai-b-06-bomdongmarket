package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.init.CropCultivationParamInitializer;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 재배 파라미터는 앞으로 작물별 자료 조사로 채워지고, 그 값은 사람이 DB 에 넣는다(#99).
// 시드가 그 값을 배포마다 추정값으로 되돌리면 조사한 사람이 같은 일을 다시 해야 한다.
//
// init 패키지가 아니라 profit 패키지에 두는 이유: ProfitReferenceData.load() 가 패키지 안에서만
// 보이고, 스프링 컨텍스트를 띄우지 않고 CSV 를 읽으려면 그 메서드를 직접 불러야 한다.
class CropCultivationParamInitializerTest {

    private static final String CURRENT_SOURCE_ID = "PROFIT_CALCULATOR_CSV_1_0_1";

    private CropCultivationParamRepository repository;
    private ProfitReferenceData referenceData;
    private CropCultivationParamInitializer initializer;

    @BeforeEach
    void setUp() {
        repository = mock(CropCultivationParamRepository.class);
        referenceData = new ProfitReferenceData();
        referenceData.load();   // 스프링 밖이라 @PostConstruct 가 돌지 않는다
        initializer = new CropCultivationParamInitializer(repository, referenceData);
    }

    @Test
    @DisplayName("테이블에 없는 작물은 CSV 값으로 넣는다")
    void insertsMissingCrops() {
        when(repository.findByCropName(anyString())).thenReturn(Optional.empty());

        initializer.run(null);

        ArgumentCaptor<CropCultivationParam> saved = ArgumentCaptor.forClass(CropCultivationParam.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        List<CropCultivationParam> rows = saved.getAllValues();
        assertThat(rows).hasSize(referenceData.supportedCropNames().size());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getSourceId()).isEqualTo(CURRENT_SOURCE_ID);
            assertThat(row.isSeedEstimate()).isTrue();
        });
    }

    @Test
    @DisplayName("사람이 조사해 넣은 값은 시드가 덮지 않는다")
    void keepsResearchedValues() {
        CropCultivationParam researched = researchedRow("상추", 9.99);
        when(repository.findByCropName(anyString()))
                .thenAnswer(invocation -> "상추".equals(invocation.getArgument(0))
                        ? Optional.of(researched)
                        : Optional.empty());

        initializer.run(null);

        // 조사값은 그대로여야 한다 — 시드의 상추 수확량으로 되돌아가면 안 된다.
        assertThat(researched.getYieldPerCycleKgM2()).isEqualTo(9.99);
        assertThat(researched.getSourceId()).isEqualTo("RESEARCH_2026_08");
        assertThat(researched.getDataStatus()).isEqualTo("RESEARCHED");
    }

    @Test
    @DisplayName("같은 시드 버전이면 추정값 행도 다시 쓰지 않는다")
    void skipsWhenSourceVersionUnchanged() {
        CropCultivationParam sameVersion = seedRow("상추", CURRENT_SOURCE_ID, 1.23);
        when(repository.findByCropName(anyString()))
                .thenAnswer(invocation -> "상추".equals(invocation.getArgument(0))
                        ? Optional.of(sameVersion)
                        : Optional.empty());

        initializer.run(null);

        assertThat(sameVersion.getYieldPerCycleKgM2()).isEqualTo(1.23);
    }

    @Test
    @DisplayName("시드 버전이 올라가면 추정값 행은 CSV 값으로 갱신한다")
    void refreshesOutdatedSeedRows() {
        CropCultivationParam outdated = seedRow("상추", "PROFIT_CALCULATOR_CSV_0_4_1", 1.23);
        when(repository.findByCropName(anyString()))
                .thenAnswer(invocation -> "상추".equals(invocation.getArgument(0))
                        ? Optional.of(outdated)
                        : Optional.empty());

        initializer.run(null);

        ProfitReferenceData.CropProduction csv = referenceData.cropProduction("상추");
        assertThat(outdated.getYieldPerCycleKgM2()).isEqualTo(csv.yieldPerCycleKgM2());
        assertThat(outdated.getSourceId()).isEqualTo(CURRENT_SOURCE_ID);
    }

    @Test
    @DisplayName("모든 작물이 이미 최신이면 아무것도 저장하지 않는다")
    void savesNothingWhenAllCurrent() {
        when(repository.findByCropName(anyString()))
                .thenAnswer(invocation -> Optional.of(
                        seedRow(invocation.getArgument(0), CURRENT_SOURCE_ID, 1.0)));

        initializer.run(null);

        verify(repository, never()).save(any());
    }

    private CropCultivationParam seedRow(String cropName, String sourceId, double yield) {
        return row(cropName, sourceId, CropCultivationParam.SEED_DATA_STATUS, yield);
    }

    private CropCultivationParam researchedRow(String cropName, double yield) {
        return row(cropName, "RESEARCH_2026_08", "RESEARCHED", yield);
    }

    private CropCultivationParam row(String cropName, String sourceId, String dataStatus, double yield) {
        return CropCultivationParam.builder()
                .cropName(cropName)
                .moduleLayers(4.0)
                .yieldPerCycleKgM2(yield)
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
                .remarks("테스트")
                .build();
    }
}
