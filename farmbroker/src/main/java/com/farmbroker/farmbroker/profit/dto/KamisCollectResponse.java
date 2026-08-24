package com.farmbroker.farmbroker.profit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 수동 시세 수집 결과. 무엇이 갱신되고 무엇이 안 됐는지 화면에서 바로 보이게 한다.
//
// 서버가 늘 떠 있지 않아(유휴 시 내려감) 매일 04시 배치가 실제로 돌지 않는 날이 많다.
// 그래서 "지금 받아오기"를 눌러 확인할 수 있어야 한다.
@Schema(description = "KAMIS 시세 수동 수집 결과")
public record KamisCollectResponse(
        @Schema(description = "수집을 시도한 기준일", example = "2026-08-23") LocalDate collectedFor,
        @Schema(description = "수집을 돌리지 않고 건너뛴 경우 true. 이유는 skipReason 에 있다", example = "false")
        boolean skipped,
        @Schema(description = """
                건너뛴 이유. skipped=false 면 null.
                DISABLED=설정이 꺼져 있거나 서비스 키 없음, ALREADY_RUNNING=이미 수집 중,
                COOLDOWN=직전 수동 수집 후 대기 시간이 남음""",
                example = "COOLDOWN", nullable = true,
                allowableValues = {"DISABLED", "ALREADY_RUNNING", "COOLDOWN"})
        String skipReason,
        @Schema(description = "새 시세로 갱신한 작물 수", example = "12") int updated,
        @Schema(description = "조사 자체가 없어 갱신하지 못한 작물 수. 비제철이면 정상이다", example = "6")
        int missing,
        @Schema(description = "조회나 저장에서 오류가 난 작물 수", example = "0") int failed,
        @Schema(description = "작물별 결과") List<Item> items) {

    // 건너뛴 이유. 화면이 "왜 안 받아왔는지"를 그대로 말할 수 있어야 한다.
    public static final String SKIP_DISABLED = "DISABLED";
    public static final String SKIP_ALREADY_RUNNING = "ALREADY_RUNNING";
    public static final String SKIP_COOLDOWN = "COOLDOWN";

    public static KamisCollectResponse skipped(LocalDate collectedFor, String reason) {
        return new KamisCollectResponse(collectedFor, true, reason, 0, 0, 0, List.of());
    }

    @Schema(description = "작물 하나의 수집 결과")
    public record Item(
            @Schema(description = "작물명", example = "상추") String cropName,
            @Schema(description = """
                    UPDATED=갱신, MISSING=조사 없음(비제철 등),
                    QUERY_FAILED=외부 조회 실패, SAVE_FAILED=저장 실패.
                    조사가 없는 것과 조회를 못 한 것은 다르다 — 후자는 장애다""",
                    example = "UPDATED",
                    allowableValues = {"UPDATED", "MISSING", "QUERY_FAILED", "SAVE_FAILED"})
            String status,
            @Schema(description = "갱신된 kg당 단가(KRW). 갱신하지 못했으면 null", example = "8750", nullable = true)
            Integer pricePerKgKrw,
            @Schema(description = "그 값의 조사일. 갱신하지 못했으면 null", example = "2026-08-21", nullable = true)
            LocalDate surveyedOn,
            @Schema(description = "중앙값을 낸 표본 수", example = "9", nullable = true) Integer sampleCount) {

        public static Item updated(String cropName, int pricePerKgKrw, LocalDate surveyedOn, int sampleCount) {
            return new Item(cropName, "UPDATED", pricePerKgKrw, surveyedOn, sampleCount);
        }

        public static Item missing(String cropName) {
            return new Item(cropName, "MISSING", null, null, null);
        }

        public static Item queryFailed(String cropName) {
            return new Item(cropName, "QUERY_FAILED", null, null, null);
        }

        public static Item saveFailed(String cropName) {
            return new Item(cropName, "SAVE_FAILED", null, null, null);
        }
    }
}
