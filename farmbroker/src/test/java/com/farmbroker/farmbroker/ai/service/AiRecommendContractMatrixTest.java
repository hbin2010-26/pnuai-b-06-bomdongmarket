package com.farmbroker.farmbroker.ai.service;

import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.ai.dto.GeminiRecommendOutput;
import com.farmbroker.farmbroker.ai.prompt.RecommendPromptBuilder;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.domain.CropDifficulty;
import com.farmbroker.farmbroker.crop.domain.LightRequirement;
import com.farmbroker.farmbroker.profit.ProfitEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 요청 모양 × 계산 가능한 작물 수의 조합을 전부 돌면서 불변식이 깨지는 칸이 있는지 본다.
//
// 개별 케이스 테스트는 내가 떠올린 경우만 덮는다. 실제로 문제가 났던 곳은 전부 "생각하지 못한
// 조합"이었다 — 후보가 1개인데 응답 하한이 2였던 것, 목적만 골랐는데 순위가 풀린 것,
// 후보가 모자랄 때 계산 불가 작물로 채운 것 모두 두 축이 만나는 칸에서 생겼다.
//
// 기대값은 아래 표에 직접 적는다. 프로덕션 코드의 판정 함수에서 끌어오면 그 함수가 틀릴 때
// 테스트도 같이 틀려서, 정작 확인하려던 것을 확인하지 못한다.
//
// 지키는 불변식:
//   (1) 후보는 언제나 계산 가능한 작물뿐이다 — 추천에 금액이 반드시 붙는다.
//   (2) 요청 모양별 후보 구성이 계약과 같다.
//   (3) 후보 수만큼의 답은 언제나 검증을 통과한다 — 아니면 그 경로는 영원히 실패한다.
class AiRecommendContractMatrixTest {

    private static final List<String> CALCULABLE_POOL = List.of("딸기", "바질", "상추", "쪽파");
    // 백과사전에만 있고 계산기에는 없는 작물.
    private static final List<String> UNCALCULABLE = List.of("로메인", "케일");

    private final AiRecommendService service =
            new AiRecommendService(null, null, null, null, null, null, null, null, null);

    private final int recommendCount =
            (int) ReflectionTestUtils.getField(AiRecommendService.class, "RECOMMEND_COUNT");

    // 요청 모양이 후보에 어떻게 작용해야 하는가.
    private enum Expected {
        /** 계산기 상위 RECOMMEND_COUNT 개로 고정. */
        FIXED_TOP,
        /** 계산 가능한 작물 전부를 열어 순서를 다시 정하게 한다. */
        WIDENED,
        /** 지정한 작물 하나만. */
        SINGLE
    }

    // 화면에서 실제로 만들 수 있는 요청 모양 전부와, 각 모양의 기대 동작.
    private static Map<String, Expected> requestShapes() {
        Map<String, Expected> shapes = new LinkedHashMap<>();
        shapes.put("아무것도 없음", Expected.FIXED_TOP);
        // 목적은 근거의 무게중심만 바꾼다. 라디오 하나로 순위가 풀리면 안 된다.
        shapes.put("목적만(수익형)", Expected.FIXED_TOP);
        shapes.put("목적만(취미형)", Expected.FIXED_TOP);
        shapes.put("자유요청만", Expected.WIDENED);
        shapes.put("목적+자유요청", Expected.WIDENED);
        shapes.put("작물지정", Expected.SINGLE);
        shapes.put("작물지정+목적", Expected.SINGLE);
        shapes.put("작물지정+자유요청", Expected.SINGLE);
        return shapes;
    }

    private static AiRecommendRequest shapeToRequest(String shape) {
        return switch (shape) {
            case "아무것도 없음" -> request(null, null, null);
            case "목적만(수익형)" -> request(null, "수익형", null);
            case "목적만(취미형)" -> request(null, "취미형", null);
            case "자유요청만" -> request(null, null, "손이 덜 가는 작물이면 좋겠습니다.");
            case "목적+자유요청" -> request(null, "취미형", "손이 덜 가는 작물이면 좋겠습니다.");
            case "작물지정" -> request("딸기", null, null);
            case "작물지정+목적" -> request("딸기", "수익형", null);
            case "작물지정+자유요청" -> request("딸기", null, "손이 덜 가는 작물이면 좋겠습니다.");
            default -> throw new IllegalArgumentException(shape);
        };
    }

    private List<String> expectedCandidates(Expected expected, int calculable) {
        return switch (expected) {
            case SINGLE -> List.of("딸기");
            case WIDENED -> CALCULABLE_POOL.subList(0, calculable);
            case FIXED_TOP -> CALCULABLE_POOL.subList(0, Math.min(calculable, recommendCount));
        };
    }

    // ── 고정 ─────────────────────────────────────────────────────────────

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

    private static List<Crop> catalog() {
        List<Crop> crops = new ArrayList<>();
        long id = 1;
        for (String name : CALCULABLE_POOL) {
            crops.add(crop(id++, name));
        }
        for (String name : UNCALCULABLE) {
            crops.add(crop(id++, name));
        }
        return crops;
    }

    private static List<ProfitEstimate> ranking(int calculableCount) {
        return CALCULABLE_POOL.stream().limit(calculableCount).map(name -> {
            ProfitEstimate estimate = mock(ProfitEstimate.class);
            when(estimate.cropName()).thenReturn(name);
            return estimate;
        }).collect(Collectors.toList());
    }

    private static AiRecommendRequest request(String preferredCrop, String purpose, String additionalInfo) {
        AiRecommendRequest request = new AiRecommendRequest();
        ReflectionTestUtils.setField(request, "spaceId", 1L);
        ReflectionTestUtils.setField(request, "preferredCrop", preferredCrop);
        ReflectionTestUtils.setField(request, "purpose", purpose);
        ReflectionTestUtils.setField(request, "additionalInfo", additionalInfo);
        return request;
    }

    // ── (1)(2) 후보 결정 ────────────────────────────────────────────────

    @Test
    @DisplayName("요청 모양 x 계산 가능 작물 수 전 조합에서 후보가 계약과 같다")
    void the_candidate_matrix_matches_the_contract() {
        for (int calculable = 1; calculable <= CALCULABLE_POOL.size(); calculable++) {
            int size = calculable;
            for (Map.Entry<String, Expected> shape : requestShapes().entrySet()) {
                List<Crop> candidates = service.resolveCandidates(
                        catalog(), ranking(size), shapeToRequest(shape.getKey()));

                assertThat(candidates)
                        .as("계산 가능 %d개 · %s", size, shape.getKey())
                        .extracting(Crop::getName)
                        .containsExactlyElementsOf(expectedCandidates(shape.getValue(), size));

                // 어떤 칸에서도 계산 불가 작물이 섞이지 않는다 — 추천에 금액이 반드시 붙는다.
                assertThat(candidates).extracting(Crop::getName)
                        .as("계산 가능 %d개 · %s", size, shape.getKey())
                        .doesNotContainAnyElementsOf(UNCALCULABLE);
            }
        }
    }

    @Test
    @DisplayName("계산 가능한 작물이 없으면 어떤 요청 모양도 추천하지 않는다")
    void nothing_calculable_never_recommends() {
        for (String shape : requestShapes().keySet()) {
            assertThatThrownBy(() ->
                    service.resolveCandidates(catalog(), List.of(), shapeToRequest(shape)))
                    .as("%s", shape)
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── (3) 후보 수와 응답 하한이 서로 모순되지 않는다 ────────────────────

    @Test
    @DisplayName("어떤 조합에서도 후보를 상한까지 쓴 답은 검증을 통과한다")
    void an_answer_that_uses_every_candidate_always_passes() {
        for (int calculable = 1; calculable <= CALCULABLE_POOL.size(); calculable++) {
            int size = calculable;
            for (String shape : requestShapes().keySet()) {
                List<Crop> candidates = service.resolveCandidates(
                        catalog(), ranking(size), shapeToRequest(shape));
                Set<Long> ids = candidates.stream().map(Crop::getId).collect(Collectors.toSet());

                List<GeminiRecommendOutput.CropItem> answer = candidates.stream()
                        .limit(recommendCount)
                        .map(crop -> new GeminiRecommendOutput.CropItem(crop.getId(), "근거"))
                        .toList();

                assertThat(service.isValidOutput(
                        new GeminiRecommendOutput(answer, List.of()), ids))
                        .as("계산 가능 %d개 · %s — 후보 %d개, 응답 %d개",
                                size, shape, candidates.size(), answer.size())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("후보가 하나뿐인 조합에서는 하나만 답해도 통과한다")
    void a_single_answer_passes_when_that_is_all_there_is() {
        for (String shape : requestShapes().keySet()) {
            for (int calculable = 1; calculable <= CALCULABLE_POOL.size(); calculable++) {
                List<Crop> candidates = service.resolveCandidates(
                        catalog(), ranking(calculable), shapeToRequest(shape));
                if (candidates.size() != 1) {
                    continue;
                }
                Set<Long> ids = candidates.stream().map(Crop::getId).collect(Collectors.toSet());

                assertThat(service.isValidOutput(new GeminiRecommendOutput(
                        List.of(new GeminiRecommendOutput.CropItem(
                                candidates.get(0).getId(), "근거")), List.of()), ids))
                        .as("계산 가능 %d개 · %s", calculable, shape)
                        .isTrue();
            }
        }
    }

    // 프롬프트가 3개를 요구하는데 하나만 온 것은 지시를 지키지 않은 응답이다.
    @Test
    @DisplayName("후보가 둘 이상인 조합에서는 하나만 답한 것을 거절한다")
    void a_single_answer_is_rejected_when_more_candidates_exist() {
        for (String shape : requestShapes().keySet()) {
            for (int calculable = 1; calculable <= CALCULABLE_POOL.size(); calculable++) {
                List<Crop> candidates = service.resolveCandidates(
                        catalog(), ranking(calculable), shapeToRequest(shape));
                if (candidates.size() < 2) {
                    continue;
                }
                Set<Long> ids = candidates.stream().map(Crop::getId).collect(Collectors.toSet());

                assertThat(service.isValidOutput(new GeminiRecommendOutput(
                        List.of(new GeminiRecommendOutput.CropItem(
                                candidates.get(0).getId(), "근거")), List.of()), ids))
                        .as("계산 가능 %d개 · %s — 후보가 %d개인데 하나만 답했다",
                                calculable, shape, candidates.size())
                        .isFalse();
            }
        }
    }

    // ── 자리 판정이 모든 위치에서 일관된다 ────────────────────────────────

    @Test
    @DisplayName("자리와 수익 순위가 같을 때만 PROFIT 이다")
    void the_pick_type_is_profit_only_when_the_position_matches() {
        for (int order = 0; order < recommendCount; order++) {
            for (int rank = 1; rank <= CALCULABLE_POOL.size(); rank++) {
                String expected = rank == order + 1
                        ? RecommendPromptBuilder.PICK_PROFIT
                        : RecommendPromptBuilder.PICK_PREFERENCE;
                assertThat(service.pickType(order, rank))
                        .as("%d순위 자리에 수익 %d위", order + 1, rank)
                        .isEqualTo(expected);
            }
            // 순위를 모르는 작물은 절대 PROFIT 으로 보이지 않아야 한다.
            assertThat(service.pickType(order, null))
                    .isEqualTo(RecommendPromptBuilder.PICK_PREFERENCE);
        }
    }

    // ── 존재하지 않는·계산 불가 작물 지정 ─────────────────────────────────

    @Test
    @DisplayName("계산 불가 작물과 없는 이름은 계산 가능 작물 수와 무관하게 거절한다")
    void bad_named_crops_are_rejected_at_every_pool_size() {
        for (int calculable = 1; calculable <= CALCULABLE_POOL.size(); calculable++) {
            int size = calculable;
            assertThatThrownBy(() -> service.resolveCandidates(
                    catalog(), ranking(size), request("로메인", null, null)))
                    .as("계산 가능 %d개 · 계산 불가 작물 지정", size)
                    .isInstanceOf(BusinessException.class)
                    .extracting(caught -> ((BusinessException) caught).getErrorCode())
                    .isEqualTo(ErrorCode.AI_CROP_NOT_CALCULABLE);

            assertThatThrownBy(() -> service.resolveCandidates(
                    catalog(), ranking(size), request("없는작물", null, null)))
                    .as("계산 가능 %d개 · 없는 작물명", size)
                    .isInstanceOf(BusinessException.class)
                    .extracting(caught -> ((BusinessException) caught).getErrorCode())
                    .isEqualTo(ErrorCode.CROP_NOT_FOUND);
        }
    }
}
