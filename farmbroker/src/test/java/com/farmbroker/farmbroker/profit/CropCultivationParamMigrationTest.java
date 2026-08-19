package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.init.CropCultivationParamInitializer;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

// CSV 를 DB 로 옮기면서 값이 바뀌지 않았는지 확인한다.
// 옮기는 과정에서 숫자가 하나라도 달라지면 예상 수익이 조용히 바뀌므로 이관 자체를 검증한다.
// DB 없이 돌도록 레포지토리는 목으로 대체하고, 저장된 행은 리스트에 담아 되돌려 준다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CropCultivationParamMigrationTest {

    @Mock
    private CropCultivationParamRepository repository;

    private ProfitReferenceData referenceData;
    private final List<CropCultivationParam> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        referenceData = new ProfitReferenceData();
        referenceData.load();
        stored.clear();

        given(repository.count()).willAnswer(call -> (long) stored.size());
        given(repository.saveAll(org.mockito.ArgumentMatchers.<Iterable<CropCultivationParam>>any()))
                .willAnswer(call -> {
                    Iterable<CropCultivationParam> given = call.getArgument(0);
                    given.forEach(stored::add);
                    return stored;
                });
        // 시더는 작물마다 있는지 보고 없는 것만 넣으므로 save(단건)로 들어온다.
        given(repository.save(org.mockito.ArgumentMatchers.any(CropCultivationParam.class)))
                .willAnswer(call -> {
                    CropCultivationParam given = call.getArgument(0);
                    stored.add(given);
                    return given;
                });
        given(repository.findByCropName(anyString())).willAnswer(call -> {
            String name = call.getArgument(0);
            return stored.stream().filter(item -> item.getCropName().equals(name)).findFirst();
        });
        given(repository.findAllByOrderByCropNameAsc()).willAnswer(call -> stored.stream()
                .sorted(Comparator.comparing(CropCultivationParam::getCropName))
                .toList());
    }

    private void migrate() {
        new CropCultivationParamInitializer(repository, referenceData).run(null);
    }

    @Test
    @DisplayName("CSV 의 모든 작물이 파라미터 값 그대로 DB 로 옮겨진다")
    void seeds_every_crop_with_identical_values() {
        migrate();

        List<String> csvCrops = referenceData.supportedCropNames();
        assertThat(stored).hasSameSizeAs(csvCrops);

        for (String cropName : csvCrops) {
            Optional<CropCultivationParam> found = repository.findByCropName(cropName);
            assertThat(found).isPresent();
            CropCultivationParam saved = found.get();
            ProfitReferenceData.CropProduction csv = referenceData.cropProduction(cropName);

            assertThat(saved.getYieldPerCycleKgM2()).isEqualTo(csv.yieldPerCycleKgM2());
            assertThat(saved.getCyclesPerMonth()).isEqualTo(csv.cyclesPerMonth());
            assertThat(saved.getMarketableRate()).isEqualTo(csv.marketableRate());
            assertThat(saved.getRequiredPpfdUmolM2S()).isEqualTo(csv.requiredPpfdUmolM2S());
            assertThat(saved.getLightingHoursDay()).isEqualTo(csv.lightingHoursDay());
            assertThat(saved.getTargetTemperatureC()).isEqualTo(csv.targetTemperatureC());
            assertThat(saved.getTargetRelativeHumidity()).isEqualTo(csv.targetRelativeHumidity());
            assertThat(saved.getDailyEvapotranspirationMm()).isEqualTo(csv.dailyEvapotranspirationMm());
            assertThat(saved.getMaterialCostPerM2CycleKrw()).isEqualTo(csv.materialCostPerM2CycleKrw());
            assertThat(saved.getOtherMaterialCostMonthKrw()).isEqualTo(csv.otherMaterialCostMonthKrw());
        }
    }

    // 실측값과 추정값이 섞이므로 근거 없이 들어간 행이 없어야 한다.
    @Test
    @DisplayName("옮긴 값에는 출처와 데이터 상태가 함께 남는다")
    void seeds_carry_their_source() {
        migrate();

        assertThat(stored).isNotEmpty().allSatisfy(param -> {
            assertThat(param.getSourceId()).isNotBlank();
            assertThat(param.getDataStatus()).isEqualTo("MVP_ESTIMATE");
            assertThat(param.getReferenceDate()).isNotNull();
        });
    }

    // 재기동마다 다시 넣으면 중복되고, 운영에서 보완한 값을 시드가 덮어쓴다.
    @Test
    @DisplayName("이미 값이 있으면 다시 넣지 않는다")
    void does_not_seed_when_table_is_not_empty() {
        migrate();
        int afterFirst = stored.size();

        migrate();

        assertThat(stored).hasSize(afterFirst);
    }

    @Test
    @DisplayName("DB 공급자가 CSV 와 같은 파라미터를 돌려준다")
    void db_provider_matches_csv() {
        migrate();
        DbCropProductionProvider provider = new DbCropProductionProvider(repository);

        for (String cropName : referenceData.supportedCropNames()) {
            assertThat(provider.hasCultivationData(cropName)).isTrue();
            assertThat(provider.cropProduction(cropName))
                    .isEqualTo(referenceData.cropProduction(cropName));
        }
        assertThat(provider.hasCultivationData("없는작물")).isFalse();
    }

    // 작물 선택 목록을 가나다순으로 보여주기로 했다(#98).
    @Test
    @DisplayName("작물 목록은 가나다순으로 돌려준다")
    void supported_crops_are_sorted() {
        migrate();
        DbCropProductionProvider provider = new DbCropProductionProvider(repository);

        assertThat(provider.supportedCropNames()).isSorted();
    }

}
