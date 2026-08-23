package com.farmbroker.farmbroker.profit.service;

import com.farmbroker.farmbroker.profit.MarketPrice;
import com.farmbroker.farmbroker.profit.MarketPriceProvider;
import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import com.farmbroker.farmbroker.profit.dto.ProfitCropResponse;
import com.farmbroker.farmbroker.profit.repository.CropCultivationParamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 수익 계산에 쓸 수 있는 작물 목록을 값의 출처와 함께 내려준다.
//
// 계산 자체는 CropProductionProvider 뒤에 숨겨 두었는데, 이 목록은 값의 성격(추정/조사)까지
// 보여 줘야 해서 테이블을 직접 읽는다. 계산 경로에 출처를 끼워 넣으면 CSV 구현이 그걸 흉내 내야 하고,
// Python 원본과의 수치 일치 검증이 출처 때문에 흔들린다.
//
// 여기가 "작물을 늘리는 길"이다 — crop_cultivation_params 에 행을 넣으면 코드·배포 없이
// 이 목록과 계산 대상에 함께 들어온다(#99).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfitCropCatalogService {

    private final CropCultivationParamRepository cropCultivationParamRepository;
    private final MarketPriceProvider marketPriceProvider;

    // 가나다순. 계산 가능한 것을 앞에 두지 않는 이유는 목록에서 작물을 이름으로 찾기 때문이다.
    public List<ProfitCropResponse> crops() {
        return cropCultivationParamRepository.findAllByOrderByCropNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private ProfitCropResponse toResponse(CropCultivationParam param) {
        Optional<MarketPrice> price = marketPriceProvider.findByCropName(param.getCropName());
        return price
                .map(found -> ProfitCropResponse.calculable(
                        param, Math.round(found.pricePerKgKrw()), found.source().name()))
                .orElseGet(() -> ProfitCropResponse.withoutPrice(param));
    }
}
