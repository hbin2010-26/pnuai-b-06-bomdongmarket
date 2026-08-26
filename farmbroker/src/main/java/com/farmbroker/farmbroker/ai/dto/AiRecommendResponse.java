package com.farmbroker.farmbroker.ai.dto;

import com.farmbroker.farmbroker.ai.domain.AiRecommendation;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// AI 추천 응답의 data 필드 DTO.
// 작물 항목의 cropId/expectedYieldKg/avgPricePerKg는 백과사전 매칭 시에만 값이 있고 아니면 null.
// profitEstimate는 이 응답에 이미 서버 계산 결과가 들어 있으므로 프론트가 따로 호출할 필요가 없다.
// (저장 전 공간의 예측이 필요하면 spaceId를 요구하지 않는 POST /profit/estimate를 쓴다)
@Getter
@Schema(description = "검증·저장된 AI 작물 추천 결과")
public class AiRecommendResponse {

    @Schema(description = "저장된 추천 이력 ID", example = "15")
    private final Long recommendationId;
    @Schema(description = "추천 대상 공간 ID", example = "1")
    private final Long spaceId;
    @Schema(description = "서버가 고른 후보 안에서 Gemini가 순서를 정하고 서버가 검증한 작물 목록. "
            + "2~3개이며, 작물을 지정했거나 계산 가능한 작물이 하나뿐이면 1개")
    private final List<RecommendedCropItem> recommendedCrops;
    @Schema(description = "공간과 추천 작물에 따른 운영 주의사항")
    private final List<String> cautions;
    @Schema(description = "추천 생성 또는 저장 시각", example = "2026-07-18T10:30:00")
    private final LocalDateTime createdAt;
    @Schema(description = "첫 추천 작물의 서버 계산 예상 수익(월평균). 계산 가능한 작물이 없으면 null. "
            + "각 작물의 값은 recommendedCrops[].profitEstimate 에 따로 들어 있다", nullable = true)
    private final ProfitEstimateResponse profitEstimate;

    private AiRecommendResponse(Long recommendationId, Long spaceId, List<RecommendedCropItem> recommendedCrops,
                                List<String> cautions, LocalDateTime createdAt,
                                ProfitEstimateResponse profitEstimate) {
        this.recommendationId = recommendationId;
        this.spaceId = spaceId;
        this.recommendedCrops = recommendedCrops;
        this.cautions = cautions;
        this.createdAt = createdAt;
        this.profitEstimate = profitEstimate;
    }

    // cautions는 엔티티에 JSON 문자열로 저장돼 있어 서비스에서 파싱해 넘긴다.
    // profitEstimate는 대표 작물이 수익 계산기 데이터에 있을 때만 채워지고 아니면 null.
    public static AiRecommendResponse of(AiRecommendation recommendation, Long spaceId,
                                         List<RecommendedCropItem> recommendedCrops, List<String> cautions,
                                         ProfitEstimateResponse profitEstimate) {
        return new AiRecommendResponse(
                recommendation.getId(),
                spaceId,
                recommendedCrops,
                cautions,
                recommendation.getCreatedAt(),
                profitEstimate
        );
    }

    @Getter
    @Schema(description = "추천 작물 항목")
    public static class RecommendedCropItem {

        @Schema(description = "서버 백과사전의 작물명", example = "상추")
        private final String cropName;
        @Schema(description = "서버 백과사전의 작물 ID", example = "1")
        private final Long cropId;
        @Schema(description = "공간·사용자 조건에 근거해 Gemini가 생성한 추천 이유")
        private final String reason;
        @Schema(description = "이 자리에 놓인 기준. PROFIT=배분수익 순위 그대로, "
                + "PREFERENCE=사용자 요청 때문에 순위와 다른 자리에 놓임",
                example = "PROFIT", allowableValues = {"PROFIT", "PREFERENCE"})
        private final String pickType;
        @Schema(description = "배분수익 순위(1부터). 계산할 수 없는 작물이면 null. "
                + "요청 때문에 순서가 바뀌었을 때 원래 수익 순위를 함께 보여주는 데 쓴다",
                example = "2", nullable = true)
        private final Integer profitRank;
        @Schema(description = "서버 계산 예상 수확량(kg): ㎡당 수확량 × 공간 면적", example = "175")
        private final Integer expectedYieldKg;
        @Schema(description = "작물 데이터에 저장된 kg당 기준 단가. Gemini 생성값이 아님", example = "7000")
        private final Integer avgPricePerKg;
        @Schema(description = "이 작물 기준 서버 계산 예상 수익. 재배 파라미터나 단가가 없으면 null",
                nullable = true)
        private final ProfitEstimateResponse profitEstimate;

        public RecommendedCropItem(String cropName, Long cropId, String reason, String pickType,
                                   Integer profitRank, Integer expectedYieldKg, Integer avgPricePerKg,
                                   ProfitEstimateResponse profitEstimate) {
            this.cropName = cropName;
            this.cropId = cropId;
            this.reason = reason;
            this.pickType = pickType;
            this.profitRank = profitRank;
            this.expectedYieldKg = expectedYieldKg;
            this.avgPricePerKg = avgPricePerKg;
            this.profitEstimate = profitEstimate;
        }
    }
}
