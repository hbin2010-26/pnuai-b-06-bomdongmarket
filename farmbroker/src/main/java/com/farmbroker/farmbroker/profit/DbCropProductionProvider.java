package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 운영 경로의 재배 파라미터 공급자 — crop_cultivation_params 테이블을 읽는다.
//
// 요청마다 조회한다. 기동 시 한 번 캐시해 두면 자료를 보완해 DB 값을 바꿔도 재시작 전까지
// 반영되지 않는다. 작물 수가 수십 건 규모라 매번 읽어도 부담이 되지 않는다.
@Primary
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DbCropProductionProvider implements CropProductionProvider {

    private final CropCultivationParamRepository repository;

    @Override
    public boolean hasCultivationData(String cropName) {
        return cropName != null && repository.findByCropName(cropName).isPresent();
    }

    @Override
    public List<String> supportedCropNames() {
        return repository.findAllByOrderByCropNameAsc().stream()
                .map(CropCultivationParam::getCropName)
                .toList();
    }

    @Override
    public ProfitReferenceData.CropProduction cropProduction(String cropName) {
        return repository.findByCropName(cropName)
                .map(DbCropProductionProvider::toRecord)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_RESPONSE_INVALID));
    }

    private static ProfitReferenceData.CropProduction toRecord(CropCultivationParam param) {
        return new ProfitReferenceData.CropProduction(
                param.getModuleLayers(),
                param.getYieldPerCycleKgM2(),
                param.getCyclesPerMonth(),
                param.getMarketableRate(),
                param.getRequiredPpfdUmolM2S(),
                param.getLightingHoursDay(),
                param.getTargetTemperatureC(),
                param.getTargetRelativeHumidity(),
                param.getDailyEvapotranspirationMm(),
                param.getSeedlingCostPerM2MonthKrw());
    }
}
