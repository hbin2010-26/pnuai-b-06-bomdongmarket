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

    // 자리(pickType)는 서버가 배분수익 순위와 견줘 정하므로 모델에게 받지 않는다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CropItem(Long cropId, String reason) {
    }
}
