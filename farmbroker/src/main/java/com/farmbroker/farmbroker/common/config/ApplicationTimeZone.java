package com.farmbroker.farmbroker.common.config;

import java.util.TimeZone;

// 애플리케이션 JVM 기본 시간대를 KST(Asia/Seoul)로 고정한다.
//
// 왜 코드에서 고정하나 — 컨테이너 TZ 환경변수만으로는 부족하다.
// 운영 배포는 docker-compose가 아니라 Render라서 compose의 TZ가 닿지 않고,
// Render 대시보드에 TZ를 넣는 것도 사람이 빠뜨릴 수 있는 설정이다.
// 코드에서 고정하면 실행 방식(로컬 · docker · Render)과 무관하게 항상 KST가 보장된다.
//
// 왜 DateTimeProvider가 아니라 기본 시간대인가 — @CreatedDate/@LastModifiedDate뿐 아니라
// 서비스 곳곳의 LocalDateTime.now()(상품 신선도 · 정렬 등)도 시스템 기본 존을 쓴다.
// 커스텀 DateTimeProvider는 감사 시각만 고치고 그 호출들은 UTC로 남긴다.
// 기본 시간대를 바꾸면 두 경로가 한 번에 KST가 된다.
//
// KAMIS 수집(#68)은 kamis.timezone으로 이미 서버 존에 의존하지 않게 막아 두었으므로
// 이 변경과 무관하게 그대로 동작한다.
public final class ApplicationTimeZone {

    public static final String ZONE_ID = "Asia/Seoul";

    private ApplicationTimeZone() {
    }

    // main()에서 SpringApplication.run() 전에 부른다 — 로깅 · 빈 초기화보다 앞서야
    // 부팅 로그 시각까지 KST로 찍힌다. now()는 호출 시점에 기본 존을 읽으므로
    // 이 시점 이후의 모든 감사 · 비즈니스 시각이 KST가 된다.
    public static void apply() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
    }
}
