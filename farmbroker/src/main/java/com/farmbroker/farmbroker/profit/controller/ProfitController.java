package com.farmbroker.farmbroker.profit.controller;

import com.farmbroker.farmbroker.common.response.ApiResponse;
import com.farmbroker.farmbroker.profit.dto.KamisCollectResponse;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateRequest;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateResponse;
import com.farmbroker.farmbroker.profit.kamis.KamisPriceCollector;
import com.farmbroker.farmbroker.profit.kamis.KamisProperties;
import com.farmbroker.farmbroker.profit.service.ProfitEstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 등록 전 수익 예측 컨트롤러.
// /ai/recommend와 달리 spaceId를 요구하지 않으므로 공간 등록 폼의 입력값만으로 호출할 수 있다.
// 권한은 SecurityConfig의 anyRequest().authenticated()를 그대로 따른다(로그인만 필요, 역할 제한 없음).
@Tag(name = "수익 예측", description = "저장 전 공간 조건으로 예상 수익을 계산하는 API (로그인 필요)")
@RestController
@RequestMapping("/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitEstimateService profitEstimateService;
    private final KamisPriceCollector kamisPriceCollector;
    private final KamisProperties kamisProperties;

    // POST /api/profit/estimate — 면적·월세만으로 예상 수익 계산
    @Operation(
            summary = "등록 전 수익 예측",
            description = """
                    공실 면적과 희망 월세만으로 결정론적 수익 계산기(ProfitCalculator)를 실행합니다.
                    Gemini를 호출하지 않으므로 아직 저장되지 않은 공간에도 사용할 수 있습니다.

                    계산기가 지원하는 모든 작물(현재 상추·딸기·바질)의 결과를 공간 제공자 예상 배분수익이
                    큰 순서로 정렬해 반환합니다. 첫 항목이 이 공간에 가장 유리한 작물입니다.

                    재배가능 비율 0.6, 다단 4층, 천장고 2.5m는 표준 가정값이며 응답에 그대로 실려 있습니다.
                    적자인 경우 금액은 음수로 그대로 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "작물별 예상 수익 목록(배분수익 내림차순)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 필드 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":false,"message":"면적은 0보다 커야 합니다.","errorCode":"VALIDATION_ERROR"}
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            )
    })
    @PostMapping("/estimate")
    public ApiResponse<List<ProfitEstimateResponse>> estimate(@RequestBody @Valid ProfitEstimateRequest request) {
        return ApiResponse.success("수익 예측이 완료되었습니다.", profitEstimateService.estimate(request));
    }

    // POST /api/profit/kamis/collect — KAMIS 시세를 지금 받아온다
    @Operation(
            summary = "KAMIS 시세 수동 수집",
            description = """
                    매일 04시 배치를 기다리지 않고 지금 시세를 받아 스냅샷에 반영합니다.
                    서버가 늘 떠 있지 않아(유휴 시 내려감) 배치가 실제로 돌지 않는 날이 많아 필요합니다.

                    작물마다 외부 API 를 한 번씩 부르고 사이에 간격을 둬서 몇 초 걸립니다.
                    그래서 남용되면 일일 할당량이 마릅니다 — 직전 수집 이후 최소 간격
                    (kamis.manual-cooldown-seconds, 기본 600초)이 지나야 다시 돌고,
                    그 전에는 skipped=true, skipReason=COOLDOWN 으로 돌려줍니다.
                    kamis.manual-collect-enabled=false 면 이 경로를 아예 닫고 새벽 배치만 씁니다.

                    이미 수집이 돌고 있으면 skipReason=ALREADY_RUNNING 입니다.
                    조사가 없는 작물(비제철 등)은 MISSING 이며 실패가 아닙니다.
                    외부 조회를 못 한 경우는 QUERY_FAILED 로 따로 나옵니다 — 이쪽은 장애입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "작물별 수집 결과"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            )
    })
    @PostMapping("/kamis/collect")
    public ApiResponse<KamisCollectResponse> collectKamis() {
        // 사람이 누른 경로다 — 쿨다운과 설정 플래그를 적용받는다.
        KamisCollectResponse result = kamisPriceCollector.collectWithReport(
                LocalDate.now(kamisProperties.zone()), true);
        return ApiResponse.success("KAMIS 시세 수집을 실행했습니다.", result);
    }
}
