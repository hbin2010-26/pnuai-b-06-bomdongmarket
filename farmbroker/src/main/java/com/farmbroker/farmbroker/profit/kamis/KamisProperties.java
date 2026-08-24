package com.farmbroker.farmbroker.profit.kamis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

// KAMIS(공공데이터포털 일별 도·소매 가격정보) 수집 설정.
// 어떤 값을 대표 시세로 삼을지는 협의 중이라(#66) 코드에 박지 않고 설정으로 뺀다.
@ConfigurationProperties(prefix = "kamis")
public record KamisProperties(
        String serviceKey,
        String baseUrl,
        // 구분코드 — 01 소매, 02 중도매.
        // 소매가는 유통마진이 붙어 있어 재배자가 넘기는 값과 다르다(상추 기준 약 2배).
        String saleType,
        // 시군구명으로 거른다. 비우면 전국 조사를 모두 본다 —
        // 지역을 좁히면 하루 1건만 잡히는 작물이 있어 기본은 전국이다.
        String region,
        // 등급명. 등급마다 값이 달라 하나로 고정한다.
        String grade,
        // 조사일이 이 일수보다 오래되면 오래된 시세로 표시한다(계산에는 그대로 쓴다).
        int freshnessDays,
        // 한 번 수집할 때 거슬러 올라가 볼 일수. 주말·공휴일에는 조사가 없어 여유를 둔다.
        int lookbackDays,
        boolean enabled,
        // 화면의 "시세 갱신" 버튼을 열어 둘지. 이 경로는 로그인만 하면 누구나 부를 수 있어,
        // 할당량이 빠듯하면 끄고 새벽 배치만 남길 수 있어야 한다(#129 리뷰).
        Boolean manualCollectEnabled,
        // 수동 수집의 최소 간격(초). 동시 실행 차단만으로는 순차 반복 호출을 막지 못한다.
        // 0 이면 간격을 두지 않는다.
        Integer manualCooldownSeconds,
        // 서버 기본 시간대가 달라도 스케줄·조회일·수집시각이 같은 날짜 기준을 쓰게 한다.
        String timezone,
        // 외부 API가 응답하지 않을 때 수집 스레드를 계속 붙잡지 않도록 연결과 읽기 정체를 제한한다.
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public KamisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B552845/perDay/price";
        }
        if (saleType == null || saleType.isBlank()) saleType = "02";
        if (grade == null || grade.isBlank()) grade = "상품";
        if (freshnessDays <= 0) freshnessDays = 7;
        if (lookbackDays <= 0) lookbackDays = 14;
        if (timezone == null || timezone.isBlank()) timezone = "Asia/Seoul";
        // 잘못된 존 이름은 기동 시점에 바로 드러나야 한다 — 새벽 배치에서 처음 터지면 찾기 어렵다.
        ZoneId.of(timezone);
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
        if (readTimeoutMs <= 0) readTimeoutMs = 5000;
        // 기본은 열어 둔다 — 서버가 유휴 시 내려가 새벽 배치를 자주 거르기 때문이다.
        if (manualCollectEnabled == null) manualCollectEnabled = true;
        if (manualCooldownSeconds == null || manualCooldownSeconds < 0) manualCooldownSeconds = 600;
    }

    // 기존 생성 경로도 환경 설정과 같은 기본값으로 동작하도록 위임한다.
    // 생성자를 하나 더 두면 Spring이 바인딩용 생성자를 못 고르고 setter 바인딩으로 넘어가
    // record에 없는 기본 생성자를 찾다가 기동이 깨진다. 그래서 정적 팩토리로 둔다.
    public static KamisProperties of(String serviceKey, String baseUrl, String saleType, String region, String grade,
                                     int freshnessDays, int lookbackDays, boolean enabled) {
        return new KamisProperties(serviceKey, baseUrl, saleType, region, grade, freshnessDays, lookbackDays, enabled,
                null, null, "Asia/Seoul", 3000, 5000);
    }

    // 수동 수집 설정만 바꾼 사본. 테스트와 설정 확인용이다.
    public KamisProperties withManualCollect(boolean allowed, int cooldownSeconds) {
        return new KamisProperties(serviceKey, baseUrl, saleType, region, grade, freshnessDays, lookbackDays,
                enabled, allowed, cooldownSeconds, timezone, connectTimeoutMs, readTimeoutMs);
    }

    public boolean usable() {
        return enabled && serviceKey != null && !serviceKey.isBlank();
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public boolean manualCollectAllowed() {
        return Boolean.TRUE.equals(manualCollectEnabled);
    }

    public String normalizedRegion() {
        return normalizeRegion(region);
    }

    // 전국(빈 값)을 null과 ""로 나눠 저장하면 같은 기준이 서로 다르게 비교된다.
    static String normalizeRegion(String region) {
        return region == null || region.isBlank() ? "" : region;
    }
}
