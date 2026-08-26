package com.farmbroker.farmbroker.ai.prompt;

import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.matching.support.SpaceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 프롬프트는 모델의 재량 범위를 정하는 곳이라, 요청 모양에 따라 지시가 실제로 갈리는지 본다.
// 여기가 어긋나면 같은 공간을 두 번 눌러도 다른 작물이 나오거나(#98),
// 작물을 지정했는데 모델이 딴 작물을 추천하는 일이 생긴다(#130 리뷰).
class RecommendPromptBuilderTest {

    private final RecommendPromptBuilder builder = new RecommendPromptBuilder();

    private static SpaceSummary space() {
        return SpaceSummary.builder()
                .id(1L)
                .area(new BigDecimal("66"))
                .monthlyRent(500_000)
                .floor(2)
                .hasWater(true)
                .hasElectricity(true)
                .hasVentilation(false)
                .description("1층 상가 공실")
                .build();
    }

    // DTO 에 세터가 없어 필드로 넣는다 — 운영 경로는 Jackson 이 같은 방식으로 채운다.
    private static AiRecommendRequest request(String preferredCrop, String purpose, String additionalInfo) {
        AiRecommendRequest request = new AiRecommendRequest();
        ReflectionTestUtils.setField(request, "spaceId", 1L);
        ReflectionTestUtils.setField(request, "preferredCrop", preferredCrop);
        ReflectionTestUtils.setField(request, "purpose", purpose);
        ReflectionTestUtils.setField(request, "additionalInfo", additionalInfo);
        return request;
    }

    private String build(AiRecommendRequest request) {
        return builder.build(space(), request, "[]", "[]");
    }

    @Test
    void without_any_request_the_model_may_not_change_the_ranking() {
        String prompt = build(request(null, null, null));

        assertThat(prompt).contains("작물을 빼거나 더하거나 순서를 바꾸지 마세요");
        // 순서를 다시 정하는 것은 요청이 있을 때만 열린다.
        assertThat(prompt).doesNotContain("순서를 정하세요");
    }

    @Test
    void a_named_crop_is_explained_alone() {
        String prompt = build(request("딸기", null, null));

        assertThat(prompt).contains("지정한 작물 하나만");
        assertThat(prompt).contains("다른 작물을 추천하거나 더 나은 작물을 제안하지 마세요");
        assertThat(prompt).doesNotContain("상위 3개");
    }

    // 목적은 근거의 무게중심만 바꾼다 — 순서를 여는 스위치가 아니다(#138 리뷰).
    @Test
    void picking_only_a_purpose_does_not_open_the_order() {
        String prompt = build(request(null, "수익형", null));

        assertThat(prompt).contains("작물을 빼거나 더하거나 순서를 바꾸지 마세요");
        assertThat(prompt).doesNotContain("순서를 정하세요");
        // 그래도 목적에 따른 서술 지시는 들어간다.
        assertThat(prompt).contains("배분수익과 비용 구조를 reason 의 앞부분에");
    }

    // 자유 요청이 있으면 계산 가능한 작물 안에서 순서를 다시 정하게 한다.
    // 순위 밖 작물을 고르면 금액이 비어 있는 카드가 나오므로 그것만은 막는다.
    @Test
    void a_free_form_request_lets_the_model_reorder_within_the_ranking() {
        String prompt = build(request(null, null, "손이 덜 가는 작물이면 좋겠습니다."));

        assertThat(prompt).contains("[서버 계산 결과]에 있는 작물 중에서");
        assertThat(prompt).contains("순서를 정하세요");
        assertThat(prompt).contains("이 목록에 없는 작물은 고르지 마세요");
        // 자리가 바뀐 작물은 방향에 관계없이 이유를 쓰게 한다 — 뒤로 밀린 작물도 설명이 필요하다.
        assertThat(prompt).contains("앞으로 당긴 것이든 뒤로 미룬 것이든");
        assertThat(prompt).contains("적자라면 적자라는 사실도");
    }

    // pickType 은 서버가 순위와 견줘 정한다. 모델에게 물으면 스스로 신고하게 하는 셈이라 믿을 수 없다.
    @Test
    void the_model_is_never_asked_for_the_pick_type() {
        for (AiRecommendRequest request : new AiRecommendRequest[]{
                request(null, null, null),
                request("딸기", null, null),
                request(null, "취미형", "손이 덜 가는 작물이면 좋겠습니다.")}) {
            assertThat(build(request)).doesNotContain("pickType");
        }
    }

    @Test
    void purpose_moves_the_weight_of_the_reason() {
        assertThat(build(request(null, "수익형", null)))
                .contains("배분수익과 비용 구조를 reason 의 앞부분에");
        assertThat(build(request(null, "취미형", null)))
                .contains("금액보다 기르는 경험을 앞에 두세요");
    }

    // 시기별 시세 자료가 없어서 모델이 제철 이야기를 지어내면 확인할 방법이 없다.
    @Test
    void the_model_is_told_not_to_talk_about_seasonality() {
        assertThat(build(request(null, "수익형", null)))
                .contains("제철·출하시기·시기별 시세");
    }
}
