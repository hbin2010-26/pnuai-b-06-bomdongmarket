package com.farmbroker.farmbroker.profit.kamis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// KAMIS 는 단위 뒤에 괄호 설명을 붙이는 품목이 있다(배추 "kg(그물망 3포기)").
// 정확히 일치로 보면 배추 도매 조사 172건이 통째로 버려지고, 그러면 그 작물은
// 시세가 없는 것처럼 보여 백과사전 기준값으로 조용히 넘어간다.
//
// 반대로 "포기"·"개" 처럼 무게가 아닌 단위는 kg 환산이 되지 않은 개당 가격이 그대로 들어와
// 반드시 걸러야 한다 — 통과시키면 매출이 자릿수째 틀어진다.
class KamisWeightUnitTest {

    private static final Pattern WEIGHT_UNIT =
            (Pattern) ReflectionTestUtils.getField(KamisPriceClient.class, "WEIGHT_UNIT");

    private boolean accepts(String unit) {
        return WEIGHT_UNIT.matcher(unit).matches();
    }

    @Test
    @DisplayName("무게 단위는 괄호 설명이 붙어도 받는다")
    void acceptsWeightUnits() {
        assertThat(accepts("kg")).isTrue();
        assertThat(accepts("g")).isTrue();
        assertThat(accepts("KG")).isTrue();
        assertThat(accepts("kg(그물망 3포기)")).isTrue();
        assertThat(accepts("kg(10개)")).isTrue();
    }

    @Test
    @DisplayName("무게가 아닌 단위는 거른다")
    void rejectsCountUnits() {
        assertThat(accepts("개")).isFalse();
        assertThat(accepts("포기")).isFalse();
        assertThat(accepts("단")).isFalse();
        assertThat(accepts("")).isFalse();
        // 'kg' 로 시작하지만 다른 단위인 경우까지 열어 주지는 않는다.
        assertThat(accepts("kgf")).isFalse();
        assertThat(accepts("10kg")).isFalse();
    }
}
