package com.farmbroker.farmbroker.profit;

// 수익 계산기의 공간 입력값.
// DB Space에는 면적과 월세만 있어 나머지 재배 파라미터는 표준 가정값을 사용한다.
// 이 가정값들은 응답에 그대로 실어 화면에서 "예상 기준"으로 노출한다(방어적 표기).
//
// 재배가능비율·천장고는 설비 사양이라 실측이 없으면 정할 수 없다(#99). 사용자가 아는 값이
// 있으면 직접 넣을 수 있게 of(...)로 받고, 없으면 fromSpace(...)의 표준 가정값을 쓴다.
//
// 다단 층 수는 1.0.1 부터 작물 속성이라 여기 없다 — 상추 4단·딸기 2단처럼 작물마다 다르다.
public record SpaceInputs(
        double totalAreaM2,
        double cultivableRatio,
        double ceilingHeightM,
        double desiredMonthlyRentKrw) {

    // 표준 가정값 — 실측 데이터가 없을 때 사용. 화면 노출용으로 공개한다.
    // 1.0.1 은 모든 공간에 재배가능비율 0.65 를 적용한다.
    public static final double DEFAULT_CULTIVABLE_RATIO = 0.65;  // 통로·설비 제외 재배 가능 바닥 비율
    public static final double DEFAULT_CEILING_HEIGHT_M = 2.5;   // 천장고

    // 사용자가 조절할 수 있는 범위. 화면의 입력 제한과 서버 검증이 같은 값을 봐야 해서 여기 둔다.
    public static final double MIN_CULTIVABLE_RATIO = 0.1;
    public static final double MAX_CULTIVABLE_RATIO = 1.0;
    public static final double MIN_CEILING_HEIGHT_M = 1.5;
    public static final double MAX_CEILING_HEIGHT_M = 10.0;

    // DB 공간 면적·월세 + 표준 가정값으로 입력을 구성한다.
    public static SpaceInputs fromSpace(double totalAreaM2, double desiredMonthlyRentKrw) {
        return new SpaceInputs(
                totalAreaM2,
                DEFAULT_CULTIVABLE_RATIO,
                DEFAULT_CEILING_HEIGHT_M,
                desiredMonthlyRentKrw);
    }

    // 설비 값을 일부만 알 때도 부를 수 있게 null 은 표준 가정값으로 채운다.
    public static SpaceInputs of(double totalAreaM2, double desiredMonthlyRentKrw,
                                Double cultivableRatio, Double ceilingHeightM) {
        return new SpaceInputs(
                totalAreaM2,
                cultivableRatio != null ? cultivableRatio : DEFAULT_CULTIVABLE_RATIO,
                ceilingHeightM != null ? ceilingHeightM : DEFAULT_CEILING_HEIGHT_M,
                desiredMonthlyRentKrw);
    }

    // 표준 가정값을 그대로 쓰고 있는지 — 화면이 "가정값 사용 중" 표기를 결정하는 데 쓴다.
    public boolean usesDefaultFacilityAssumptions() {
        return cultivableRatio == DEFAULT_CULTIVABLE_RATIO
                && ceilingHeightM == DEFAULT_CEILING_HEIGHT_M;
    }
}
