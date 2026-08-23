package com.farmbroker.farmbroker.profit.service;

import com.farmbroker.farmbroker.profit.MarketPriceProvider;
import com.farmbroker.farmbroker.profit.ProfitCalculator;
import com.farmbroker.farmbroker.profit.ProfitEstimate;
import com.farmbroker.farmbroker.profit.SpaceInputs;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateRequest;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// 등록 전 수익 예측 서비스.
// 계산기가 지원하는 작물을 모두 돌려 공간 제공자 배분수익이 큰 순으로 정렬해 돌려준다.
// 프론트는 첫 항목을 대표 작물로 쓰고 나머지는 비교안으로 보여준다.
//
// AI 추천(/ai/recommend)도 같은 순위를 쓴다. 두 화면이 같은 공간에 다른 작물을 내놓던 원인이
// 한쪽은 계산기, 한쪽은 Gemini가 작물을 고르는 구조였기 때문이라, 선택과 순서는 이 한 곳이 정한다.
@Service
@RequiredArgsConstructor
public class ProfitEstimateService {

    private final ProfitCalculator profitCalculator;
    private final MarketPriceProvider marketPriceProvider;

    public List<ProfitEstimateResponse> estimate(ProfitEstimateRequest request) {
        return rank(request.toSpaceInputs(), request.getCropNames()).stream()
                .map(ProfitEstimateResponse::from)
                .toList();
    }

    // 배분수익 내림차순으로 계산한다. cropNames가 비어 있으면 계산 가능한 작물 전체를 쓴다.
    //
    // 재배 파라미터가 있어도 단가를 모르는 작물은 매출을 계산할 수 없으므로 목록에서 뺀다.
    // (단가 출처는 MarketPriceProvider가 정한다 — 백과사전 기준값, 이후 KAMIS 시세)
    public List<ProfitEstimate> rank(SpaceInputs inputs, Collection<String> cropNames) {
        return candidates(cropNames).stream()
                .flatMap(cropName -> marketPriceProvider.findByCropName(cropName).stream()
                        .map(price -> profitCalculator.estimate(inputs, cropName, price)))
                .sorted(Comparator.comparingDouble(ProfitEstimate::landlordExpectedIncomeKrw).reversed())
                .toList();
    }

    // 요청이 지정한 작물 중 계산기가 아는 것만 남긴다.
    // 모르는 이름을 400으로 되돌리지 않는 이유: 화면이 /profit/crops 목록으로 고르게 되어 있어
    // 어긋나는 경우는 목록이 오래된 것뿐이고, 그때는 결과가 비는 편이 낫다.
    private List<String> candidates(Collection<String> cropNames) {
        if (cropNames == null || cropNames.isEmpty()) {
            return profitCalculator.supportedCrops();
        }
        Set<String> wanted = cropNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.KOREAN))
                .collect(Collectors.toSet());
        return profitCalculator.supportedCrops().stream()
                .filter(name -> wanted.contains(name.toLowerCase(Locale.KOREAN)))
                .toList();
    }
}
