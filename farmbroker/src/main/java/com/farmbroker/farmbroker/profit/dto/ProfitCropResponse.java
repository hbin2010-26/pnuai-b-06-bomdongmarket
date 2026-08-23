package com.farmbroker.farmbroker.profit.dto;

import com.farmbroker.farmbroker.profit.domain.CropCultivationParam;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

// 수익 계산에 쓸 수 있는 작물 하나. 화면의 작물 선택 목록이 이걸 그대로 그린다.
//
// 값이 어디서 왔는지(dataStatus·sourceId·referenceDate)를 함께 내린다. 재배 파라미터가 전부
// 추정값인 단계라, 화면이 숫자만 보여 주면 실측처럼 읽힌다(#99).
//
// calculable=false 인 항목도 뺴지 않고 내린다. 재배 파라미터는 들어왔는데 단가가 없어 계산이
// 안 되는 작물을, 담당자가 목록에서 바로 알아볼 수 있어야 하기 때문이다.
@Schema(description = "수익 계산 대상 작물과 그 값의 출처")
public record ProfitCropResponse(
        @Schema(description = "작물명", example = "상추") String cropName,

        @Schema(description = "재배 파라미터와 단가가 모두 있어 계산 가능한지", example = "true")
        boolean calculable,
        @Schema(description = "계산할 수 없는 이유. calculable=true면 null",
                example = "단가 정보가 없습니다.", nullable = true)
        String blockedReason,

        @Schema(description = """
                값의 신뢰도 3단계.
                MVP_ESTIMATE=근거 없는 초기 추정값, RESEARCHED=문헌·통계에서 찾은 값(sourceId 참고),
                MEASURED=직접 측정한 값(referenceDate 가 측정일)""",
                example = "MVP_ESTIMATE",
                allowableValues = {"MVP_ESTIMATE", "RESEARCHED", "MEASURED"})
        String dataStatus,
        @Schema(description = "값을 가져온 출처 식별자", example = "PROFIT_CALCULATOR_CSV_0_3_1", nullable = true)
        String sourceId,
        @Schema(description = "값의 기준일", example = "2026-07-04", nullable = true)
        LocalDate referenceDate,
        @Schema(description = "값에 대한 단서", nullable = true) String remarks,

        @Schema(description = "kg당 판매단가(KRW). 단가가 없으면 null", example = "8000", nullable = true)
        Long pricePerKgKrw,
        @Schema(description = "단가 출처. SEED=작물 백과사전 기준값, KAMIS=농산물유통정보 시세",
                example = "SEED", nullable = true)
        String priceSource) {

    // 재배 파라미터만 있고 단가가 없는 작물. 담당자가 무엇을 더 채워야 하는지 보이게 한다.
    public static ProfitCropResponse withoutPrice(CropCultivationParam param) {
        return new ProfitCropResponse(
                param.getCropName(), false, "단가 정보가 없습니다. 작물 백과사전에 kg당 단가를 넣어야 계산됩니다.",
                param.getDataStatus(), param.getSourceId(), param.getReferenceDate(), param.getRemarks(),
                null, null);
    }

    public static ProfitCropResponse calculable(CropCultivationParam param, long pricePerKgKrw, String priceSource) {
        return new ProfitCropResponse(
                param.getCropName(), true, null,
                param.getDataStatus(), param.getSourceId(), param.getReferenceDate(), param.getRemarks(),
                pricePerKgKrw, priceSource);
    }
}
