package com.farmbroker.farmbroker.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Gemini가 반환하는 JSON 본문을 역직렬화하는 내부 DTO.
// 모델이 프롬프트에 명시한 형식({recommendedCrops, cautions})과 1:1 대응한다.
// 배치 제안은 걷어냈다 — 그림 없는 텍스트 한 줄로는 쓸 수 있는 정보가 아니었다(#98).
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRecommendOutput(
        List<CropItem> recommendedCrops,
        List<String> cautions
) {

    // pickType 은 이 작물이 계산기 순위에서 왔는지(PROFIT), 사용자 취향으로 골랐는지(PREFERENCE).
    // 모델이 빠뜨리거나 엉뚱한 값을 넣을 수 있어 서버가 다시 정규화한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CropItem(Long cropId, String pickType, String reason) {
    }
}
