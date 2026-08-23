package com.farmbroker.farmbroker.profit.dto;

import com.farmbroker.farmbroker.profit.SpaceInputs;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// 등록 전 수익 예측(POST /profit/estimate) 요청 바디.
// spaceId가 아니라 면적·월세를 직접 받으므로 아직 저장되지 않은 공간도 계산할 수 있다.
// 필드명과 검증 메시지는 SpaceCreateRequest의 같은 항목과 맞춘다.
//
// 설비 값(재배가능비율·층수·천장고)과 작물 지정은 모두 선택이다. 비워 두면 표준 가정값과
// 계산 가능한 작물 전체를 쓰므로, 기존 호출부는 그대로 두어도 동작이 바뀌지 않는다.
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "등록 전 공간 조건 기반 수익 예측 요청")
public class ProfitEstimateRequest {

    private static final String AREA_MIN_EXCLUSIVE = "0.0";
    private static final long RENT_MIN = 0;
    private static final int MAX_CROP_NAMES = 30;

    private static final String MSG_AREA_REQUIRED = "면적은 필수입니다.";
    private static final String MSG_AREA_POSITIVE = "면적은 0보다 커야 합니다.";
    private static final String MSG_RENT_REQUIRED = "월세는 필수입니다.";
    private static final String MSG_RENT_MIN = "월세는 0 이상이어야 합니다.";
    private static final String MSG_RATIO_RANGE = "재배 가능 비율은 0.1 이상 1.0 이하여야 합니다.";
    private static final String MSG_CEILING_RANGE = "천장고는 1.5m 이상 10m 이하여야 합니다.";
    private static final String MSG_CROPS_SIZE = "작물은 30개까지 지정할 수 있습니다.";

    @Schema(description = "공실 전체면적(㎡)", example = "66", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = MSG_AREA_REQUIRED)
    @DecimalMin(value = AREA_MIN_EXCLUSIVE, inclusive = false, message = MSG_AREA_POSITIVE)
    private BigDecimal area;

    @Schema(description = "공간 제공자가 원하는 월세(KRW)", example = "500000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = MSG_RENT_REQUIRED)
    @Min(value = RENT_MIN, message = MSG_RENT_MIN)
    private Integer monthlyRent;

    @Schema(description = "통로·설비를 뺀 재배 가능 바닥 비율(0.1~1.0). 비우면 표준 가정값 0.65",
            example = "0.65", nullable = true)
    @DecimalMin(value = "0.1", message = MSG_RATIO_RANGE)
    @DecimalMax(value = "1.0", message = MSG_RATIO_RANGE)
    private BigDecimal cultivableRatio;

    @Schema(description = "천장고(m, 1.5~10). 비우면 표준 가정값 2.5", example = "2.5", nullable = true)
    @DecimalMin(value = "1.5", message = MSG_CEILING_RANGE)
    @DecimalMax(value = "10.0", message = MSG_CEILING_RANGE)
    private BigDecimal ceilingHeightM;

    @Schema(description = "계산할 작물명. 비우면 계산 가능한 작물 전체를 배분수익 순으로 반환",
            example = "[\"상추\"]", nullable = true)
    @Size(max = MAX_CROP_NAMES, message = MSG_CROPS_SIZE)
    private List<String> cropNames;

    // 표준 가정값 채우기는 SpaceInputs가 하고, 여기서는 요청값을 그대로 넘긴다.
    public SpaceInputs toSpaceInputs() {
        return SpaceInputs.of(
                area.doubleValue(),
                monthlyRent,
                cultivableRatio != null ? cultivableRatio.doubleValue() : null,
                ceilingHeightM != null ? ceilingHeightM.doubleValue() : null);
    }
}
