package com.farmbroker.farmbroker.ai.prompt;

import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.matching.support.SpaceSummary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// AI 추천 프롬프트 조립기. 프롬프트 수정이 잦을 것이므로 서비스 코드와 분리한다.
//
// 작물 선택과 순서는 서버 수익 계산기가 정하고, 모델은 근거 문장과 주의사항만 쓴다.
// 예전에는 "적합한 작물을 제안하세요" 한 줄이라 같은 공간을 두 번 실행해도 다른 작물이 나왔고,
// 등록 화면(계산기)과 상세 화면(모델)이 서로 다른 작물을 내놓았다(#98).
//
// 사용자 요청이 있을 때만 순위를 벗어날 수 있다. 그래야 "요청을 넣으면 결과가 달라진다"가
// 사용자가 조절할 수 있는 축이 되고, 요청이 없으면 결과가 흔들리지 않는다.
//
// 사용자 입력(additionalInfo 등)은 길이 제한(DTO 검증) + 프롬프트 하단 배치로 인젝션 영향 최소화.
@Component
public class RecommendPromptBuilder {

    public String build(SpaceSummary space, AiRecommendRequest request,
                        String cropCatalogJson, String serverEstimateJson) {
        return """
                당신은 도심 실내 다단 스마트팜 컨설턴트입니다.

                작물의 선택과 순서는 서버 수익 계산기가 이미 정했습니다([서버 계산 결과]).
                당신이 할 일은 각 작물이 이 공간에 왜 맞는지 근거 문장을 쓰고, 운영 주의사항을 적는 것입니다.
                %s

                [근거를 쓸 때 볼 것] 위에서부터 우선합니다.
                1. 서버가 계산한 배분수익 — 이미 순위에 반영돼 있습니다. 다시 계산하지 마세요.
                2. 공간 설비와 작물 요구의 맞음새 — 전기가 없으면 광 요구가 큰 작물은 불리하고,
                   환기가 없으면 습도가 높아야 하는 작물이 어렵습니다.
                3. 재배 난이도 — 처음 하는 사람에게 어려운 작물이면 근거에 그 점을 적으세요.
                4. 재배기간 — 회전이 빠르면 초기 현금흐름이 빨라집니다.

                [숫자를 쓰는 규칙]
                금액·수확량·전력 같은 숫자는 [서버 계산 결과]에 있는 값만 인용하세요.
                직접 계산하거나 어림한 숫자를 쓰면 안 됩니다. 인용할 값이 없으면 숫자 없이 쓰세요.

                [공간 조건]
                - 면적: %s㎡ / 층: %s / 월세: %s원
                - 수도: %s, 전기: %s, 환기: %s
                - 설명: %s

                [서버 계산 결과] 배분수익이 큰 순서입니다. reason 은 이 순서대로 쓰세요.
                %s

                [작물 백과사전 후보 JSON]
                %s

                [사용자 요청]
                - 희망 작물: %s
                - 목적: %s
                - 추가 정보: %s

                반드시 아래 JSON 형식으로만 최종 응답하세요. 다른 텍스트를 포함하지 마세요.
                {
                  "recommendedCrops": [{"cropId": 1, "reason": "..."}],
                  "cautions": ["..."]
                }
                """.formatted(
                selectionRule(request),
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
                orDefault(request.getAdditionalInfo())
        );
    }

    // 요청이 없으면 순위를 그대로 따르게 하고, 있으면 그 요청만큼 벗어날 수 있게 한다.
    private String selectionRule(AiRecommendRequest request) {
        if (!hasUserRequest(request)) {
            return """
                    [서버 계산 결과]에 있는 작물 전부에 대해 그 순서대로 reason 을 쓰세요.
                    작물을 빼거나 더하거나 순서를 바꾸지 마세요.""";
        }
        return """
                아래 [사용자 요청]이 있습니다. [서버 계산 결과]를 기준으로 삼되, 요청에 더 맞는 작물이
                순위 밖에 있으면 [작물 백과사전 후보] 안에서 2~3개로 바꿔도 됩니다.
                바꾼 경우 그 작물의 reason 에 순위를 벗어난 이유를 밝히세요.""";
    }

    public static boolean hasUserRequest(AiRecommendRequest request) {
        return StringUtils.hasText(request.getPreferredCrop())
                || StringUtils.hasText(request.getPurpose())
                || StringUtils.hasText(request.getAdditionalInfo());
    }

    private String yesNo(boolean value) {
        return value ? "있음" : "없음";
    }

    private String orDefault(String value) {
        return StringUtils.hasText(value) ? value : "없음";
    }
}
