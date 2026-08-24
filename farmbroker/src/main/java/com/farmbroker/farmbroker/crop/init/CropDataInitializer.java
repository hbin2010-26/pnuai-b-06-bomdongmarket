package com.farmbroker.farmbroker.crop.init;

import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.domain.CropDataSource;
import com.farmbroker.farmbroker.crop.domain.CropDifficulty;
import com.farmbroker.farmbroker.crop.domain.LightRequirement;
import com.farmbroker.farmbroker.crop.repository.CropRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 서버 기동 시 작물 백과사전 시드 데이터를 적재하는 로더.
//
// avgPricePerKg는 수익 계산의 단일 가격 소스다(SeedPriceProvider가 이 값을 읽는다).
// 수익 계산기 CSV(crop_sale_info.csv)는 자바로 이관하지 않았으므로, 계산기가 재배
// 파라미터를 가진 작물은 반드시 여기에 같은 단가로 들어와 있어야 한다. 빠지면 그 작물은
// 단가가 없어 계산에서 조용히 제외된다 — 애플민트·쪽파·병풀이 실제로 그렇게 빠져 있었다.
// CropSeedPriceCoverageTest가 이 대응을 지킨다.
//
// 이름으로 없는 것만 넣으므로 재기동해도 중복되지 않는다. 예전에는 crops 테이블이 비어
// 있을 때만 넣어서, 시드에 작물을 더해도 이미 돌고 있는 환경에는 영원히 들어가지 않았다.
//
// 외부 공공 API(농사로) 실시간 연동 대신 시드 데이터로 시작하는 것은 팀 확정 방침 —
// 이후 NONGSARO 배치 적재로 확장한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CropDataInitializer implements ApplicationRunner {

    private final CropRepository cropRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Crop> seeds = seedCrops();
        Map<String, Crop> stored = cropRepository.findAll().stream()
                .collect(Collectors.toMap(Crop::getName, Function.identity(), (first, second) -> first));

        List<Crop> missing = seeds.stream()
                .filter(seed -> !stored.containsKey(seed.getName()))
                .toList();
        if (!missing.isEmpty()) {
            cropRepository.saveAll(missing);
            log.info("[작물 시드] {}종을 새로 넣었습니다: {}", missing.size(),
                    missing.stream().map(Crop::getName).toList());
        }

        syncSeedPrices(seeds, stored);
    }

    // 시드에서 단가를 고쳤을 때 이미 저장된 행에도 반영한다.
    // 사람이 손본 행(dataSource != SEED)은 건드리지 않는다 — 조사한 값을 배포마다
    // 시드값으로 되돌리면 조사한 사람이 같은 일을 다시 해야 한다.
    private void syncSeedPrices(List<Crop> seeds, Map<String, Crop> stored) {
        for (Crop seed : seeds) {
            Crop crop = stored.get(seed.getName());
            if (crop == null || !crop.isSeed()) {
                continue;
            }
            if (!Objects.equals(crop.getAvgPricePerKg(), seed.getAvgPricePerKg())) {
                log.info("[작물 시드] {} 단가를 {} → {} 으로 맞췄습니다.",
                        crop.getName(), crop.getAvgPricePerKg(), seed.getAvgPricePerKg());
                crop.applySeedPrice(seed.getAvgPricePerKg());
            }
        }
    }

    // 테스트가 계산기 CSV 와의 대응을 확인하므로 패키지 범위로 둔다.
    List<Crop> seedCrops() {
        return List.of(
                crop("상추", "잎채소", 30, CropDifficulty.EASY, 15.0, 22.0, 65.0, LightRequirement.MEDIUM, 3.5, 8000,
                        "저온성 잎채소로 실내 다단 재배에 가장 널리 쓰인다. 재배 기간이 짧고 초기 설비 부담이 낮아 입문용으로 적합하다."),
                crop("로메인", "잎채소", 35, CropDifficulty.EASY, 15.0, 22.0, 65.0, LightRequirement.MEDIUM, 3.0, 8000,
                        "샐러드 수요가 꾸준한 잎채소. 상추와 재배 조건이 비슷해 함께 기르기 좋다."),
                crop("케일", "잎채소", 50, CropDifficulty.NORMAL, 15.0, 25.0, 60.0, LightRequirement.HIGH, 2.5, 10000,
                        "영양가가 높아 주스·샐러드용 수요가 많다. 광량이 충분해야 잎이 두껍게 자란다."),
                crop("루꼴라", "잎채소", 25, CropDifficulty.EASY, 15.0, 22.0, 60.0, LightRequirement.MEDIUM, 2.0, 15000,
                        "재배 기간이 매우 짧고 회전율이 높다. 향이 강해 레스토랑 납품 단가가 좋은 편이다."),
                crop("청경채", "잎채소", 35, CropDifficulty.EASY, 18.0, 23.0, 65.0, LightRequirement.MEDIUM, 3.0, 6000,
                        "수분 관리만 잘하면 실패가 적은 잎채소. 볶음·쌈 수요가 꾸준하다."),
                crop("시금치", "잎채소", 40, CropDifficulty.NORMAL, 10.0, 20.0, 60.0, LightRequirement.MEDIUM, 2.5, 8000,
                        "저온을 선호해 겨울철 실내 재배에 유리하다. 고온에서는 웃자람에 주의해야 한다."),
                // 단가는 계산기 crop_sale_info.csv 기준이다(25,000). 20,000으로 들어가 있어
                // 같은 작물의 단가가 계산기와 서버에서 달랐다.
                crop("바질", "허브", 40, CropDifficulty.NORMAL, 20.0, 28.0, 60.0, LightRequirement.HIGH, 1.5, 25000,
                        "단가가 높고 소규모 공간에서도 재배 효율이 좋은 대표 허브. 고온성이라 보온·광량 관리가 중요하다."),
                crop("민트", "허브", 35, CropDifficulty.EASY, 18.0, 25.0, 60.0, LightRequirement.MEDIUM, 1.8, 25000,
                        "생명력이 강해 초보자도 기르기 쉽다. 음료·디저트용 수요가 꾸준하다."),
                crop("고수", "허브", 30, CropDifficulty.NORMAL, 17.0, 24.0, 60.0, LightRequirement.MEDIUM, 1.5, 20000,
                        "동남아 음식 수요 증가로 단가가 좋아진 허브. 더위에 약해 온도 관리가 필요하다."),
                crop("딸기", "과채류", 90, CropDifficulty.HARD, 15.0, 23.0, 65.0, LightRequirement.HIGH, 2.0, 30000,
                        "수경 재배 프리미엄 작물. 재배 기간이 길고 수분(꽃가루받이)·온도 관리 난도가 높지만 단가가 좋다."),
                crop("방울토마토", "과채류", 75, CropDifficulty.NORMAL, 20.0, 28.0, 65.0, LightRequirement.HIGH, 4.0, 9000,
                        "㎡당 수확량이 많은 과채류. 지주 설치가 필요하고 충분한 광량이 확보돼야 한다."),
                crop("무순", "새싹채소", 7, CropDifficulty.EASY, 18.0, 25.0, 70.0, LightRequirement.LOW, 1.0, 12000,
                        "일주일 만에 수확하는 새싹채소. 광량이 거의 필요 없어 창고형 공간에서도 재배할 수 있다."),

                // ── 계산기에는 재배 파라미터가 있는데 백과사전에 없어 계산에서 빠져 있던 3종 ──
                // 단가는 계산기 crop_sale_info.csv 값을 그대로 쓴다.
                // 나머지 항목은 crop_production_info.csv 에서 역산한 추정값이다 —
                // 목표습도는 (target_relative_humidity - 0.05) x 100, 재배기간은 30 / cycles_per_month,
                // 광 요구는 required_ppfd 기준(150 LOW / 200 MEDIUM / 250 HIGH),
                // ㎡당 수확량은 다단 재배대 기준값을 기존 3종의 비율(약 1.9배)로 나눈 바닥면적 환산값이다.
                crop("애플민트", "허브", 38, CropDifficulty.EASY, 19.0, 25.0, 60.0, LightRequirement.LOW, 0.9, 60000,
                        "향이 강해 음료·디저트용으로 쓰이는 민트 품종. 광 요구가 낮아 전기 부담이 작다."),
                crop("쪽파", "잎채소", 38, CropDifficulty.EASY, 17.0, 23.0, 60.0, LightRequirement.MEDIUM, 1.3, 10000,
                        "손질해서 바로 쓰는 수요가 꾸준한 잎채소. 물을 많이 먹어 제습 부하가 큰 편이다."),
                crop("병풀", "허브", 30, CropDifficulty.NORMAL, 21.0, 27.0, 55.0, LightRequirement.MEDIUM, 1.1, 20000,
                        "화장품 원료로 쓰이는 허브. 회전이 빠르지만 고온·다습을 좋아해 환경 관리가 필요하다.")
        );
    }

    private Crop crop(String name, String category, int growingPeriodDays, CropDifficulty difficulty,
                      Double tempMin, Double tempMax, Double humidity, LightRequirement light,
                      Double yieldPerSqmKg, Integer avgPricePerKg, String description) {
        return Crop.builder()
                .name(name)
                .category(category)
                .growingPeriodDays(growingPeriodDays)
                .difficulty(difficulty)
                .optimalTempMin(tempMin)
                .optimalTempMax(tempMax)
                .optimalHumidity(humidity)
                .lightRequirement(light)
                .yieldPerSqmKg(yieldPerSqmKg)
                .avgPricePerKg(avgPricePerKg)
                .description(description)
                .dataSource(CropDataSource.SEED)
                .build();
    }
}
