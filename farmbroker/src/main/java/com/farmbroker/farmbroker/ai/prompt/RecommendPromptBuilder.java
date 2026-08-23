package com.farmbroker.farmbroker.ai.prompt;

import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.matching.support.SpaceSummary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// AI 추천 프롬프트 조립기. 프롬프트 수정이 잦을 것이므로 서비스 코드와 분리한다.
//
// 역할 구분: 작물 선택과 순서는 서버 수익 계산기가 정하고, 모델은 근거 문장과 주의사항만 쓴다.
// 예전에는 "적합한 작물을 제안하세요" 한 줄이라 같은 공간을 두 번 실행해도 다른 작물이 나왔고,
// 등록 화면(계산기)과 상세 화면(모델)이 서로 다른 작물을 내놓았다(#98).
//
// 모델이 순위를 벗어날 수 있는 자리는 "취향 추천" 한 칸뿐이다(#98 리뷰).
// 그 한 칸은 수익이 아니라 난이도·재배기간·활용성으로 고르는 자리라, 계산기가 답할 수 없는
// 질문에만 모델을 쓰고 수익 순위는 계산기 결과가 그대로 남는다.
//
// 사용자 입력(additionalInfo 등)은 길이 제한(DTO 검증) + 프롬프트 하단 배치로 인젝션 영향 최소화.
@Component
public class RecommendPromptBuilder {

    // 추천 항목이 어떤 기준으로 뽑혔는지. 화면이 "취향 추천" 배지를 붙이는 데 쓴다.
    public static final String PICK_PROFIT = "PROFIT";
    public static final String PICK_PREFERENCE = "PREFERENCE";

    private static final String PURPOSE_PROFIT = "수익형";
    private static final String PURPOSE_HOBBY = "취미형";

    public String build(SpaceSummary space, AiRecommendRequest request,
                        String cropCatalogJson, String serverEstimateJson) {
        return """
                당신은 도심 실내 다단 스마트팜 컨설턴트입니다.

                작물의 선택과 순서는 서버 수익 계산기가 이미 정했습니다([서버 계산 결과]).
                당신이 할 일은 각 작물이 이 공간에 왜 맞는지 근거 문장을 쓰고, 운영 주의사항을 적는 것입니다.

                [무엇을 고를 것인가]
                %s

                [근거를 쓸 때 볼 것] 위에서부터 우선합니다.
                1. 서버가 계산한 배분수익 — 이미 순위에 반영돼 있습니다. 다시 계산하지 마세요.
                2. 공간 설비와 작물 요구의 맞음새 — 전기가 없으면 광 요구가 큰 작물은 불리하고,
                   환기가 없으면 습도가 높아야 하는 작물이 어렵습니다.
                3. 재배 난이도(difficulty) — 처음 하는 사람에게 어려운 작물이면 근거에 그 점을 적으세요.
                4. 재배기간(growingPeriodDays) — 회전이 빠르면 초기 현금흐름이 빨라집니다.
                %s

                [숫자를 쓰는 규칙]
                금액·수확량·전력 같은 숫자는 [서버 계산 결과]에 있는 값만 인용하세요.
                직접 계산하거나 어림한 숫자를 쓰면 안 됩니다. 인용할 값이 없으면 숫자 없이 쓰세요.

                [쓰면 안 되는 것]
                제철·출하시기·시기별 시세처럼 계절에 따라 달라지는 이야기는 쓰지 마세요.
                지금 주어진 자료에 시기별 가격이 없어서 확인할 방법이 없습니다.

                [공간 조건]
                - 면적: %s㎡ / 층: %s / 월세: %s원
                - 수도: %s, 전기: %s, 환기: %s
                - 설명: %s

                [서버 계산 결과] 배분수익이 큰 순서입니다. reason 은 이 순서대로 쓰세요.
                %s

                [작물 백과사전 후보 JSON]
                %s

                [사용자 요청]
                - 궁금한 작물: %s
                - 목적: %s
                - 기타 취향 및 요청사항: %s

                반드시 아래 JSON 형식으로만 최종 응답하세요. 다른 텍스트를 포함하지 마세요.
                pickType 은 계산기 순위에서 온 작물이면 "%s", 취향으로 고른 작물이면 "%s" 입니다.
                {
                  "recommendedCrops": [{"cropId": 1, "pickType": "%s", "reason": "..."}],
                  "cautions": ["..."]
                }
                """.formatted(
                selectionRule(request),
                purposeRule(request),
                space.getArea(),
                space.getFloor() != null ? space.getFloor() + "층" : "정보 없음",
                space.getMonthlyRent(),
                yesNo(space.isHasWater()),
                yesNo(space.isHasElectricity()),
                yesNo(space.isHasVentilation()),
                orDefault(space.getDescription()),
                serverEstimateJson,
                cropCatalogJson,
                orDefault(request.getPreferredCrop()),
                orDefault(request.getPurpose()),
                orDefault(request.getAdditionalInfo()),
                PICK_PROFIT,
                PICK_PREFERENCE,
                PICK_PROFIT
        );
    }

    // 무엇을 몇 개 고를지. 사용자가 얼마나 구체적으로 물었는지에 따라 세 갈래다.
    private String selectionRule(AiRecommendRequest request) {
        // 1) 작물을 콕 집어 물었으면 그 작물만 설명한다. 다른 작물을 같이 내놓으면
        //    사용자가 물어본 것에 답하지 않고 화제를 돌리는 셈이다(#98·#130 리뷰).
        if (StringUtils.hasText(request.getPreferredCrop())) {
            return """
                    사용자가 작물을 지정했습니다. 지정한 작물 하나만 reason 을 쓰고 pickType 은 "%s" 입니다.
                    다른 작물을 추천하거나 더 나은 작물을 제안하지 마세요.
                    그 작물이 이 공간에 불리하더라도 작물을 바꾸지 말고, 왜 불리한지를 reason 에 쓰고
                    보완할 방법을 cautions 에 적으세요.""".formatted(PICK_PROFIT);
        }
        // 2) 아무 요청도 없으면 계산기 순위가 곧 답이다. 모델이 고를 여지를 두지 않는다.
        if (!hasUserRequest(request)) {
            return """
                    [서버 계산 결과]에 있는 작물 전부에 대해 그 순서대로 reason 을 쓰세요.
                    pickType 은 모두 "%s" 입니다. 작물을 빼거나 더하거나 순서를 바꾸지 마세요.""".formatted(PICK_PROFIT);
        }
        // 3) 작물 지정 없이 목적·요청만 온 경우. 순위는 그대로 두고 취향 추천 한 칸만 더한다.
        return """
                [서버 계산 결과] 상위 3개를 그 순서대로 쓰세요. pickType 은 "%s" 입니다.
                순서를 바꾸거나 이 3개를 다른 작물로 교체하지 마세요.

                그 뒤에 [사용자 요청]에 가장 잘 맞는 작물 1개를 [작물 백과사전 후보]에서 골라
                마지막 항목으로 덧붙이세요. 이 항목의 pickType 은 "%s" 입니다.
                수익이 아니라 난이도·재배기간·활용성으로 고르고, 왜 이 요청에 맞는지 reason 에 쓰세요.
                이미 위 3개에 있는 작물이거나 요청에 맞는 작물이 없으면 이 항목은 넣지 마세요."""
                .formatted(PICK_PROFIT, PICK_PREFERENCE);
    }

    // 목적에 따라 근거의 무게중심이 달라진다. 같은 작물이라도 수익형과 취미형에 할 말이 다르다.
    private String purposeRule(AiRecommendRequest request) {
        String purpose = request.getPurpose();
        if (PURPOSE_HOBBY.equals(purpose)) {
            return """
                    사용자 목적이 취미형입니다. 금액보다 기르는 경험을 앞에 두세요 —
                    손이 얼마나 가는지, 수확까지 얼마나 걸리는지, 수확물을 어디에 쓸 수 있는지를
                    reason 의 앞부분에 쓰고 금액은 뒤에 한 번만 언급하세요.""";
        }
        if (PURPOSE_PROFIT.equals(purpose)) {
            return """
                    사용자 목적이 수익형입니다. 배분수익과 비용 구조를 reason 의 앞부분에 쓰세요.
                    적자로 나온 작물은 감추지 말고 어느 비용이 큰지를 함께 적으세요.""";
        }
        return "";
    }

    public static boolean hasUserRequest(AiRecommendRequest request) {
        return StringUtils.hasText(request.getPreferredCrop())
                || StringUtils.hasText(request.getPurpose())
                || StringUtils.hasText(request.getAdditionalInfo());
    }

    // 작물을 지정했으면 모델이 고를 것이 없다 — 후보도 그 작물 하나로 줄인다.
    public static boolean picksSingleCrop(AiRecommendRequest request) {
        return StringUtils.hasText(request.getPreferredCrop());
    }

    private String yesNo(boolean value) {
        return value ? "있음" : "없음";
    }

    private String orDefault(String value) {
        return StringUtils.hasText(value) ? value : "없음";
    }
}
