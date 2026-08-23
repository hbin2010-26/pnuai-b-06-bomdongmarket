package com.farmbroker.farmbroker.profit.init;

import com.farmbroker.farmbroker.profit.ProfitReferenceData;
import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

// crop_production_info.csv 의 값을 crop_cultivation_params 테이블에 채우는 시드 로더.
//
// 값을 손으로 다시 적지 않고 CSV 를 그대로 읽어 넣는다 — 옮기는 과정에서 숫자가 바뀌면
// Python 원본과의 수치 일치가 깨지고, 그 사실을 알아채기 어렵다.
//
// ── 시드와 조사값의 경계 ──
// 재배 파라미터는 앞으로 작물별 자료 조사로 채워질 예정이고(#99), 그 값은 사람이 DB 에 넣는다.
// 그래서 이 로더는 dataStatus 가 MVP_ESTIMATE 인 행, 즉 자기가 넣은 추정값만 건드린다.
//
//   - 테이블에 없는 작물  → 넣는다 (CSV 에 행을 더하면 작물이 늘어난다)
//   - MVP_ESTIMATE 인 행 → SOURCE_ID 가 바뀌었을 때만 갱신한다 (CSV 를 손보면 반영된다)
//   - 그 밖의 행         → 건드리지 않는다 (조사·실측값은 배포로 되돌아가지 않는다)
//
// 조사한 값을 넣을 때는 dataStatus 를 MVP_ESTIMATE 가 아닌 값으로 두면 된다.
// 작물을 늘리는 것 자체는 이 로더 없이도 된다 — 테이블에 행을 직접 넣으면 계산 대상에 바로 들어온다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CropCultivationParamInitializer implements ApplicationRunner {

    // CSV 는 원본 계산기 프로젝트에서 그대로 옮겨온 추정값이다.
    // CSV 값을 손보면 이 문자열도 함께 올려야 기존 추정값 행이 갱신된다.
    private static final String SOURCE_ID = "PROFIT_CALCULATOR_CSV_1_0_1";
    private static final String DATA_STATUS = CropCultivationParam.SEED_DATA_STATUS;
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 4);
    private static final String REMARKS = "crop_production_info.csv 에서 이관한 추정값 — 작물별 자료 조사로 보완 필요";

    private final CropCultivationParamRepository repository;
    private final ProfitReferenceData referenceData;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int inserted = 0;
        int refreshed = 0;
        int kept = 0;

        for (String cropName : referenceData.supportedCropNames()) {
            Optional<CropCultivationParam> existing = repository.findByCropName(cropName);
            if (existing.isEmpty()) {
                repository.save(toEntity(cropName));
                inserted++;
                continue;
            }

            CropCultivationParam row = existing.get();
            if (!row.isSeedEstimate()) {
                kept++;   // 조사값 — 시드가 덮지 않는다
                continue;
            }
            if (SOURCE_ID.equals(row.getSourceId())) {
                continue; // 같은 시드 버전이면 그대로 둔다
            }
            row.replaceValuesFrom(toEntity(cropName));
            refreshed++;
        }

        if (inserted + refreshed > 0) {
            log.info("작물 재배 파라미터 시드: {}종 추가, {}종 갱신(추정값), {}종은 조사값이라 유지",
                    inserted, refreshed, kept);
        }
    }

    private CropCultivationParam toEntity(String cropName) {
        ProfitReferenceData.CropProduction crop = referenceData.cropProduction(cropName);
        return CropCultivationParam.builder()
                .cropName(cropName)
                .moduleLayers(crop.moduleLayers())
                .yieldPerCycleKgM2(crop.yieldPerCycleKgM2())
                .cyclesPerMonth(crop.cyclesPerMonth())
                .marketableRate(crop.marketableRate())
                .requiredPpfdUmolM2S(crop.requiredPpfdUmolM2S())
                .lightingHoursDay(crop.lightingHoursDay())
                .targetTemperatureC(crop.targetTemperatureC())
                .targetRelativeHumidity(crop.targetRelativeHumidity())
                .dailyEvapotranspirationMm(crop.dailyEvapotranspirationMm())
                .seedlingCostPerM2MonthKrw(crop.seedlingCostPerM2MonthKrw())
                .sourceId(SOURCE_ID)
                .dataStatus(DATA_STATUS)
                .referenceDate(REFERENCE_DATE)
                .remarks(REMARKS)
                .build();
    }
}
