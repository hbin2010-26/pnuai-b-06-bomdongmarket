package com.farmbroker.farmbroker.profit;

import java.util.List;

// 작물 재배 파라미터를 어디서 읽을지 가리는 지점.
//
// 값은 이제 DB(crop_cultivation_params)에서 오지만, 계산 로직은 출처를 몰라도 되도록 분리한다.
// Python 원본과의 수치 일치를 검증하는 테스트는 DB 없이 CSV 구현으로 그대로 돌 수 있어야 한다.
public interface CropProductionProvider {

    // 재배 파라미터를 가진 작물인지. 단가는 MarketPriceProvider 가 따로 판단한다.
    boolean hasCultivationData(String cropName);

    // 재배 파라미터를 가진 작물 목록.
    List<String> supportedCropNames();

    // 없는 작물을 물으면 예외를 던진다 — 호출부가 hasCultivationData 로 먼저 걸러야 한다.
    ProfitReferenceData.CropProduction cropProduction(String cropName);
}
