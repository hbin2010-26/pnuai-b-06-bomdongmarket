package com.farmbroker.farmbroker.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 추천(POST /ai/recommend) 요청 바디 DTO.
// 자유 입력 필드의 길이 제한은 프롬프트 인젝션 완화를 겸한다.
@Getter
@NoArgsConstructor
@Schema(description = "Gemini 작물 추천 요청")
public class AiRecommendRequest {

    @Schema(description = "추천을 실행할 공간 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "공간 ID는 필수입니다.")
    private Long spaceId;

    @Schema(description = """
            궁금한 작물명. GET /profit/crops 의 이름을 그대로 보냅니다.
            보내면 AI가 다른 작물을 추천하지 않고 이 작물만 설명합니다.""",
            example = "상추", maxLength = 30, nullable = true)
    @Size(max = 30, message = "희망 작물은 30자 이하여야 합니다.")
    private String preferredCrop;

    @Schema(description = """
            재배 목적. 수익형 또는 취미형. 근거 문장의 무게중심이 달라집니다 —
            수익형은 배분수익과 비용 구조를, 취미형은 난이도와 재배기간을 앞에 둡니다.""",
            example = "수익형", maxLength = 100, nullable = true,
            allowableValues = {"수익형", "취미형"})
    @Size(max = 100, message = "목적은 100자 이하여야 합니다.")
    private String purpose;

    @Schema(description = "기타 취향 및 요청사항", example = "손이 덜 가는 작물이면 좋겠습니다.",
            maxLength = 500, nullable = true)
    @Size(max = 500, message = "추가 정보는 500자 이하여야 합니다.")
    private String additionalInfo;
}
