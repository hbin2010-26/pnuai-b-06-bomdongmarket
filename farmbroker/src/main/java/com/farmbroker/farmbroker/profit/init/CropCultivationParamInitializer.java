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
import java.util.List;

// crop_production_info.csv 의 값을 crop_cultivation_params 테이블로 옮기는 1회 적재 로더.
//
// 값을 손으로 다시 적지 않고 CSV 를 그대로 읽어 넣는다 — 옮기는 과정에서 숫자가 바뀌면
// Python 원본과의 수치 일치가 깨지고, 그 사실을 알아채기 어렵다.
//
// 테이블이 비어 있을 때만 넣으므로 재기동해도 중복되지 않고, 운영에서 값을 보완한 뒤에도
// 시드가 덮어쓰지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CropCultivationParamInitializer implements ApplicationRunner {

    // CSV 는 원본 계산기 프로젝트에서 그대로 옮겨온 추정값이다.
    private static final String SOURCE_ID = "PROFIT_CALCULATOR_CSV_0_3_1";
    private static final String DATA_STATUS = "MVP_ESTIMATE";
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 4);
    private static final String REMARKS = "crop_production_info.csv 에서 이관한 추정값 — 작물별 자료 조사로 보완 필요";

    private final CropCultivationParamRepository repository;
    private final ProfitReferenceData referenceData;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        List<CropCultivationParam> seeds = referenceData.supportedCropNames().stream()
                .map(this::toEntity)
                .toList();
        repository.saveAll(seeds);
        log.info("작물 재배 파라미터 {}종을 CSV 에서 적재했습니다", seeds.size());
    }

    private CropCultivationParam toEntity(String cropName) {
        ProfitReferenceData.CropProduction crop = referenceData.cropProduction(cropName);
        return CropCultivationParam.builder()
                .cropName(cropName)
                .yieldPerCycleKgM2(crop.yieldPerCycleKgM2())
                .cyclesPerMonth(crop.cyclesPerMonth())
                .marketableRate(crop.marketableRate())
                .requiredPpfdUmolM2S(crop.requiredPpfdUmolM2S())
                .lightingHoursDay(crop.lightingHoursDay())
                .targetTemperatureC(crop.targetTemperatureC())
                .targetRelativeHumidity(crop.targetRelativeHumidity())
                .dailyEvapotranspirationMm(crop.dailyEvapotranspirationMm())
                .materialCostPerM2CycleKrw(crop.materialCostPerM2CycleKrw())
                .otherMaterialCostMonthKrw(crop.otherMaterialCostMonthKrw())
                .sourceId(SOURCE_ID)
                .dataStatus(DATA_STATUS)
                .referenceDate(REFERENCE_DATE)
                .remarks(REMARKS)
                .build();
    }
}
