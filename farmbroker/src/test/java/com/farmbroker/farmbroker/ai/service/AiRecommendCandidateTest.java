package com.farmbroker.farmbroker.ai.service;

import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.ai.prompt.RecommendPromptBuilder;
import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.domain.CropDifficulty;
import com.farmbroker.farmbroker.crop.domain.LightRequirement;
import com.farmbroker.farmbroker.profit.ProfitEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 추천된 작물에는 금액이 반드시 붙어야 한다. 그래서 모델에게 주는 후보는 언제나
// "계산 가능한 작물"(= 배분수익 순위에 오른 작물)로 제한한다.
//
// 전에는 사용자 요청이 있으면 백과사전 전체를 후보로 줘서, 모델이 계산기에 없는 작물을 고르면
// 금액이 비어 있는 카드가 나왔다. 요청을 넣을수록 결과가 나빠지는 셈이었다.
class AiRecommendCandidateTest {

    // 백과사전에는 있지만 계산기에는 없는 작물(로메인·케일)을 섞어 둔다.
    private static final List<String> CATALOG =
            List.of("딸기", "바질", "상추", "쪽파", "로메인", "케일");
    private static final List<String> CALCULABLE = List.of("딸기", "바질", "상추", "쪽파");

    private final AiRecommendService service =
            new AiRecommendService(null, null, null, null, null, null, null, null, null);

    private static Crop crop(String name) {
        return Crop.builder()
                .name(name)
                .category("잎채소")
                .growingPeriodDays(30)
                .difficulty(CropDifficulty.EASY)
                .lightRequirement(LightRequirement.MEDIUM)
                .avgPricePerKg(10000)
                .build();
    }

    private static List<Crop> catalog() {
        return CATALOG.stream().map(AiRecommendCandidateTest::crop).toList();
    }

    // resolveCandidates 는 작물명만 읽으므로 그 부분만 흉내 낸다.
    private static List<ProfitEstimate> ranking(String... cropNames) {
        return Arrays.stream(cropNames).map(name -> {
            ProfitEstimate estimate = mock(ProfitEstimate.class);
            when(estimate.cropName()).thenReturn(name);
            return estimate;
        }).toList();
    }

    private static AiRecommendRequest request(String preferredCrop, String purpose, String additionalInfo) {
        AiRecommendRequest request = new AiRecommendRequest();
        ReflectionTestUtils.setField(request, "spaceId", 1L);
        ReflectionTestUtils.setField(request, "preferredCrop", preferredCrop);
        ReflectionTestUtils.setField(request, "purpose", purpose);
        ReflectionTestUtils.setField(request, "additionalInfo", additionalInfo);
        return request;
    }

    private List<String> candidateNames(AiRecommendRequest request) {
        return service.resolveCandidates(catalog(), ranking(CALCULABLE.toArray(String[]::new)), request)
                .stream().map(Crop::getName).toList();
    }

    @Test
    @DisplayName("요청이 있어도 계산할 수 없는 작물은 후보에 넣지 않는다")
    void a_request_never_opens_the_candidates_beyond_calculable_crops() {
        List<String> names = candidateNames(request(null, "취미형", "손이 덜 가는 작물이면 좋겠습니다."));

        assertThat(names).containsExactlyElementsOf(CALCULABLE);
        assertThat(names).doesNotContain("로메인", "케일");
    }

    @Test
    @DisplayName("요청이 없으면 계산기 상위 3개만 후보로 준다")
    void without_a_request_only_the_top_three_are_candidates() {
        assertThat(candidateNames(request(null, null, null)))
                .containsExactly("딸기", "바질", "상추");
    }

    @Test
    @DisplayName("작물을 지정하면 그 작물 하나만 후보로 준다")
    void a_named_crop_is_the_only_candidate() {
        assertThat(candidateNames(request("상추", null, null))).containsExactly("상추");
    }

    // 응답 검증이 2~3개를 요구하므로, 계산 가능한 작물이 1개뿐이면 후보를 그것만 줄 수 없다.
    @Test
    @DisplayName("계산 가능한 작물이 2개도 안 되면 백과사전 전체를 후보로 연다")
    void falls_back_to_the_catalog_when_almost_nothing_is_calculable() {
        List<Crop> candidates =
                service.resolveCandidates(catalog(), ranking("딸기"), request(null, "수익형", null));

        assertThat(candidates).hasSameSizeAs(CATALOG);
    }

    // 요청 때문에 순서가 바뀐 자리는 화면이 "취향" 으로 구분해 보여줘야 한다.
    @Test
    @DisplayName("자리는 배분수익 순위와 견줘 서버가 정한다")
    void the_pick_type_is_decided_by_comparing_with_the_profit_rank() {
        // 1순위 자리에 수익 1위가 그대로 있으면 PROFIT
        assertThat(service.pickType(0, 1)).isEqualTo(RecommendPromptBuilder.PICK_PROFIT);
        // 1순위 자리에 수익 3위가 올라왔으면 요청 때문에 바뀐 것
        assertThat(service.pickType(0, 3)).isEqualTo(RecommendPromptBuilder.PICK_PREFERENCE);
        // 계산할 수 없는 작물(순위 없음)이 섞여 들어와도 PROFIT 으로 보이지 않게 한다
        assertThat(service.pickType(1, null)).isEqualTo(RecommendPromptBuilder.PICK_PREFERENCE);
    }
}
