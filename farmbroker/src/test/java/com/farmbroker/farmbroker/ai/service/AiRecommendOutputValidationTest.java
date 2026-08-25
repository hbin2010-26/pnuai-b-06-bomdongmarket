package com.farmbroker.farmbroker.ai.service;

import com.farmbroker.farmbroker.ai.dto.GeminiRecommendOutput;
import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.domain.CropDifficulty;
import com.farmbroker.farmbroker.crop.domain.LightRequirement;
import com.farmbroker.farmbroker.profit.ProfitEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 모델 응답을 그대로 믿지 않는 마지막 관문이다. 여기가 느슨하면 화면에 이상한 추천이 그대로 뜨고,
// 반대로 너무 조이면 정상 응답이 전부 걸려 추천 자체가 되지 않는다.
//
// 실제로 후자가 일어났다 — 응답 개수 하한이 2였던 탓에 작물을 지정한 요청은 후보가 하나뿐이라
// 언제나 검증에 걸렸다(#138 리뷰). 그때까지 이 함수에는 테스트가 하나도 없었다.
class AiRecommendOutputValidationTest {

    private static final Set<Long> CANDIDATES = Set.of(1L, 2L, 3L);

    private final AiRecommendService service =
            new AiRecommendService(null, null, null, null, null, null, null, null, null);

    private static GeminiRecommendOutput output(List<GeminiRecommendOutput.CropItem> crops,
                                               List<String> cautions) {
        return new GeminiRecommendOutput(crops, cautions);
    }

    private static GeminiRecommendOutput.CropItem item(Long cropId, String reason) {
        return new GeminiRecommendOutput.CropItem(cropId, reason);
    }

    private boolean valid(List<GeminiRecommendOutput.CropItem> crops) {
        return service.isValidOutput(output(crops, List.of()), CANDIDATES);
    }

    // ── 하한 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("작물 하나만 담은 응답도 통과한다")
    void a_single_crop_answer_is_valid() {
        assertThat(valid(List.of(item(1L, "이 공간에 맞습니다.")))).isTrue();
    }

    @Test
    @DisplayName("빈 응답은 거절한다")
    void an_empty_answer_is_rejected() {
        assertThat(valid(List.of())).isFalse();
    }

    // ── 상한 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("화면에 놓는 수보다 많이 오면 거절한다")
    void more_crops_than_the_screen_shows_is_rejected() {
        int limit = (int) ReflectionTestUtils.getField(AiRecommendService.class, "RECOMMEND_COUNT");
        List<GeminiRecommendOutput.CropItem> tooMany = new java.util.ArrayList<>();
        for (long id = 1; id <= limit + 1; id++) {
            tooMany.add(item(id, "근거"));
        }

        assertThat(service.isValidOutput(output(tooMany, List.of()),
                Set.of(1L, 2L, 3L, 4L, 5L))).isFalse();
    }

    // ── 내용 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 작물을 두 번 담으면 거절한다")
    void a_duplicated_crop_is_rejected() {
        assertThat(valid(List.of(item(1L, "근거"), item(1L, "다른 근거")))).isFalse();
    }

    @Test
    @DisplayName("후보에 없는 작물을 담으면 거절한다")
    void a_crop_outside_the_candidates_is_rejected() {
        assertThat(valid(List.of(item(1L, "근거"), item(99L, "근거")))).isFalse();
    }

    @Test
    @DisplayName("작물 ID 가 없으면 거절한다")
    void a_missing_crop_id_is_rejected() {
        assertThat(valid(List.of(item(null, "근거")))).isFalse();
    }

    @Test
    @DisplayName("근거가 비어 있으면 거절한다")
    void a_blank_reason_is_rejected() {
        assertThat(valid(List.of(item(1L, "   ")))).isFalse();
        assertThat(valid(List.of(item(1L, null)))).isFalse();
    }

    @Test
    @DisplayName("주의사항 목록이 아예 없으면 거절한다")
    void a_missing_cautions_list_is_rejected() {
        assertThat(service.isValidOutput(output(List.of(item(1L, "근거")), null), CANDIDATES))
                .isFalse();
        assertThat(service.isValidOutput(output(null, List.of()), CANDIDATES)).isFalse();
        assertThat(service.isValidOutput(null, CANDIDATES)).isFalse();
    }

    // ── 요청이 없을 때는 모델이 순서를 흔들어도 계산기 순위가 남는다 ────────

    private static Crop crop(long id, String name) {
        Crop crop = Crop.builder()
                .name(name)
                .category("잎채소")
                .growingPeriodDays(30)
                .difficulty(CropDifficulty.EASY)
                .lightRequirement(LightRequirement.MEDIUM)
                .avgPricePerKg(10000)
                .build();
        ReflectionTestUtils.setField(crop, "id", id);
        return crop;
    }

    private static Map<String, ProfitEstimate> estimates(String... cropNames) {
        Map<String, ProfitEstimate> map = new LinkedHashMap<>();
        Arrays.stream(cropNames).forEach(name -> {
            ProfitEstimate estimate = mock(ProfitEstimate.class);
            when(estimate.landlordExpectedIncomeKrw()).thenReturn(1_000_000.0);
            map.put(name, estimate);
        });
        return map;
    }

    @Test
    @DisplayName("모델이 순서를 바꿔 보내도 계산기 순위대로 되돌린다")
    void the_ranking_order_survives_a_reordered_answer() {
        List<Crop> candidates = List.of(crop(1L, "딸기"), crop(2L, "바질"), crop(3L, "상추"));

        List<GeminiRecommendOutput.CropItem> aligned = service.alignToRanking(
                List.of(item(3L, "상추 근거"), item(1L, "딸기 근거"), item(2L, "바질 근거")),
                candidates, estimates("딸기", "바질", "상추"));

        assertThat(aligned).extracting(GeminiRecommendOutput.CropItem::cropId)
                .containsExactly(1L, 2L, 3L);
    }

    // 없는 근거를 모델처럼 지어내지 않고 계산 결과를 옮겨 적는다.
    @Test
    @DisplayName("모델이 근거를 빠뜨린 작물은 계산 결과로 채운다")
    void a_missing_reason_is_filled_from_the_calculation() {
        List<Crop> candidates = List.of(crop(1L, "딸기"), crop(2L, "바질"));

        List<GeminiRecommendOutput.CropItem> aligned = service.alignToRanking(
                List.of(item(1L, "딸기 근거")), candidates, estimates("딸기", "바질"));

        assertThat(aligned).hasSize(2);
        assertThat(aligned.get(1).reason()).contains("서버 계산 기준");
    }
}
