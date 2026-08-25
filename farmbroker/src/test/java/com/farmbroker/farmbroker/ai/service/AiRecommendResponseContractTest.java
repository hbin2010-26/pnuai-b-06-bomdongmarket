package com.farmbroker.farmbroker.ai.service;

import com.farmbroker.farmbroker.ai.client.GeminiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 후보 개수와 응답 개수 계약이 어긋나면 그 경로는 언제나 실패한다.
//
// 작물을 지정한 요청은 후보가 하나뿐인데 응답 스키마가 최소 2개를 요구하고 있었다.
// 유효 ID 가 하나라 중복 없이 조건을 만족할 방법이 없어, 그 경로는 늘 AI_RESPONSE_INVALID
// 로 끝나거나 과거 추천으로 fallback 됐다(#138 리뷰). 프롬프트 문구만 보는 테스트로는
// 잡히지 않아 스키마 자체와 서버 검증의 하한을 함께 확인한다.
class AiRecommendResponseContractTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> recommendedCropsSchema() {
        GeminiClient client = new GeminiClient(
                new ObjectMapper(), "test-key", "gemini-test", "https://example.invalid");
        Map<String, Object> config =
                (Map<String, Object>) ReflectionTestUtils.invokeMethod(client, "buildGenerationConfig");
        Map<String, Object> schema = (Map<String, Object>) config.get("responseSchema");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return (Map<String, Object>) properties.get("recommendedCrops");
    }

    @Test
    @DisplayName("응답 스키마는 작물 1개만 담은 답도 허용한다")
    void the_schema_allows_a_single_crop_answer() {
        assertThat(recommendedCropsSchema())
                .as("작물을 지정한 요청은 그 작물 하나만 답해야 한다")
                .containsEntry("minItems", 1);
    }

    @Test
    @DisplayName("응답 스키마 상한이 화면에 놓는 추천 수와 같다")
    void the_schema_upper_bound_matches_the_recommend_count() {
        int recommendCount = (int) ReflectionTestUtils.getField(
                AiRecommendService.class, "RECOMMEND_COUNT");

        assertThat(recommendedCropsSchema())
                .as("스키마가 더 많이 허용하면 서버 검증에서 걸려 추천이 실패한다")
                .containsEntry("maxItems", recommendCount);
    }
}
