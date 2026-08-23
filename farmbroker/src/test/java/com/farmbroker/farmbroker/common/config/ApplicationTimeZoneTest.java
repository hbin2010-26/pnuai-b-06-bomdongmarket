package com.farmbroker.farmbroker.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

// apply()가 JVM 기본 시간대를 KST로 고정하는지 검증한다.
// 기본 시간대는 JVM 전역 상태라, 다른 테스트에 새지 않도록 원래 값을 저장했다가 되돌린다.
class ApplicationTimeZoneTest {

    private TimeZone original;

    @BeforeEach
    void saveDefault() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefault() {
        TimeZone.setDefault(original);
    }

    @Test
    @DisplayName("apply()는 기본 시간대와 무관하게 KST로 고정한다")
    void appliesKst() {
        // 시작 시간대가 무엇이든 결과가 같아야 하므로 일부러 UTC에서 출발한다.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ApplicationTimeZone.apply();

        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
