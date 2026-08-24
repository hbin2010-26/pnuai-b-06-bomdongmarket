package com.farmbroker.farmbroker.crop.init;

import com.farmbroker.farmbroker.crop.domain.Crop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 수익 계산기는 재배 파라미터를 crop_production_info.csv 에서 읽고, 단가는 작물 백과사전
// (crops.avg_price_per_kg) 한 곳에서만 읽는다. 두 곳이 서로 다른 파일이라 한쪽에만 작물을
// 넣으면 계산에서 조용히 빠진다 — 애플민트·쪽파·병풀이 실제로 그렇게 빠져 있었다.
//
// 화면에는 "단가 정보가 없습니다"로만 보이고 에러가 나지 않아 알아채기 어렵다.
// 그래서 두 목록의 대응을 테스트로 고정한다.
class CropSeedPriceCoverageTest {

    private static final String PRODUCTION_CSV = "profit/crop_production_info.csv";
    // 계산기 원본의 단가표. 자바로 이관하지 않았으므로 여기서만 기대값으로 쓴다.
    private static final Map<String, Integer> CALCULATOR_PRICES = Map.of(
            "상추", 8000,
            "딸기", 30000,
            "바질", 25000,
            "애플민트", 60000,
            "쪽파", 10000,
            "병풀", 20000);

    private final CropDataInitializer initializer = new CropDataInitializer(null);

    private Map<String, Crop> seedByName() {
        return initializer.seedCrops().stream()
                .collect(Collectors.toMap(Crop::getName, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private static Set<String> calculatorCropNames() {
        Set<String> names = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(PRODUCTION_CSV).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // 헤더
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    names.add(line.split(",", -1)[0].trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(PRODUCTION_CSV + " 를 읽지 못했습니다.", e);
        }
        return names;
    }

    @Test
    @DisplayName("계산기가 아는 작물은 모두 백과사전에 단가와 함께 있다")
    void every_calculator_crop_has_a_seed_price() {
        Map<String, Crop> seeds = seedByName();

        for (String cropName : calculatorCropNames()) {
            Crop crop = seeds.get(cropName);
            assertThat(crop)
                    .as("%s 는 %s 에 재배 파라미터가 있는데 백과사전 시드에 없습니다 — "
                            + "단가가 없으면 수익 계산에서 조용히 빠집니다.", cropName, PRODUCTION_CSV)
                    .isNotNull();
            assertThat(crop.getAvgPricePerKg())
                    .as("%s 의 백과사전 단가", cropName)
                    .isNotNull()
                    .isPositive();
        }
    }

    @Test
    @DisplayName("백과사전 단가가 계산기 원본 단가와 같다")
    void seed_prices_match_the_calculator_price_table() {
        Map<String, Crop> seeds = seedByName();

        CALCULATOR_PRICES.forEach((cropName, expected) -> assertThat(seeds.get(cropName))
                .as("%s 가 백과사전 시드에 있어야 합니다.", cropName)
                .isNotNull()
                .extracting(Crop::getAvgPricePerKg)
                .as("%s 단가는 계산기 crop_sale_info.csv 와 같아야 합니다.", cropName)
                .isEqualTo(expected));
    }

    // 계산기에 없는 작물은 백과사전에만 있어도 된다(추천 후보로는 쓰인다).
    // 다만 단가가 비어 있으면 나중에 재배 파라미터가 들어오는 순간 또 조용히 빠지므로 함께 막는다.
    @Test
    @DisplayName("백과사전 시드는 모두 단가를 가진다")
    void every_seed_crop_has_a_price() {
        assertThat(initializer.seedCrops())
                .allSatisfy(crop -> assertThat(crop.getAvgPricePerKg())
                        .as("%s 단가", crop.getName())
                        .isNotNull()
                        .isPositive());
    }
}
