package com.farmbroker.farmbroker.ai.service;

import com.farmbroker.farmbroker.ai.client.GeminiClient;
import com.farmbroker.farmbroker.ai.domain.AiRecommendation;
import com.farmbroker.farmbroker.ai.domain.RecommendedCrop;
import com.farmbroker.farmbroker.ai.dto.AiRecommendOutcome;
import com.farmbroker.farmbroker.ai.dto.AiRecommendRequest;
import com.farmbroker.farmbroker.ai.dto.AiRecommendResponse;
import com.farmbroker.farmbroker.ai.dto.GeminiRecommendOutput;
import com.farmbroker.farmbroker.profit.dto.ProfitEstimateResponse;
import com.farmbroker.farmbroker.ai.prompt.RecommendPromptBuilder;
import com.farmbroker.farmbroker.profit.ProfitEstimate;
import com.farmbroker.farmbroker.profit.SpaceInputs;
import com.farmbroker.farmbroker.profit.service.ProfitEstimateService;
import com.farmbroker.farmbroker.ai.repository.AiRecommendationRepository;
import com.farmbroker.farmbroker.common.exception.BusinessException;
import com.farmbroker.farmbroker.common.exception.ErrorCode;
import com.farmbroker.farmbroker.crop.domain.Crop;
import com.farmbroker.farmbroker.crop.repository.CropRepository;
import com.farmbroker.farmbroker.space.domain.Space;
import com.farmbroker.farmbroker.matching.support.SpaceSummary;
import com.farmbroker.farmbroker.matching.support.SpaceContractAdapter;
import com.farmbroker.farmbroker.user.domain.User;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// AI 작물/공간 활용 추천 로직 (structured output 방식).
// 흐름: 공간 검증 → 서버 계산기로 작물 순위 산출 → 그 순위를 Gemini에 주고 근거 문장 요청 →
//   의미 검증 → 순위와 맞춤 → 추천 이력 저장(crop_id 연결) → 작물별 계산값과 함께 응답.
//
// 작물의 선택과 순서는 계산기가 정한다. 예전에는 Gemini가 백과사전 12개 중에서 직접 골라
// 등록 화면(계산기 기준)과 상세 화면(Gemini 기준)이 같은 공간에 다른 작물을 내놓았다(#98).
// 사용자 요청(희망 작물·목적·추가 정보)이 있을 때만 순위를 벗어날 수 있다.
//
// Gemini 장애(AI_TIMEOUT/AI_QUOTA_EXCEEDED) 시 같은 공간의 최근 저장 결과를 fallback으로 반환한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRecommendService {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendService.class);

    // 화면에 버튼으로 놓을 추천 작물 수. 계산기 순위 상위 몇 개를 후보로 줄지도 이 값을 따른다.
    private static final int RECOMMEND_COUNT = 3;

    private final GeminiClient geminiClient;
    private final RecommendPromptBuilder promptBuilder;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final SpaceContractAdapter spaceContractAdapter; // BE2 SpaceService 계약 제공 시 교체
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ProfitEstimateService profitEstimateService; // 작물 선택·순서·금액의 단일 출처

    @Transactional
    public AiRecommendOutcome recommend(Long userId, AiRecommendRequest request) {
        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        SpaceSummary space = spaceContractAdapter.getSummaryById(request.getSpaceId()); // 미존재 시 SPACE_NOT_FOUND
        if (space.isDeleted()) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND); // 삭제된 공간은 본 모듈에서 404 처리 (status는 무관)
        }

        List<Crop> crops = cropRepository.findAll();
        Map<Long, Crop> cropById = crops.stream()
                .collect(Collectors.toMap(Crop::getId, Function.identity()));

        // 배분수익 내림차순. 계산 가능한 작물이 없으면 비어 있을 수 있다.
        List<ProfitEstimate> ranking = rankCrops(space);
        Map<String, ProfitEstimate> estimateByCropName = ranking.stream()
                .collect(Collectors.toMap(ProfitEstimate::cropName, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        List<Crop> candidates = resolveCandidates(crops, ranking, request);
        String prompt = promptBuilder.build(space, request,
                toCatalogJson(candidates), toServerEstimateJson(ranking));

        GeminiRecommendOutput output;
        try {
            output = callStructuredAndParse(prompt,
                    candidates.stream().map(Crop::getId).collect(Collectors.toSet()));
        } catch (BusinessException e) {
            AiRecommendOutcome fallback = tryFallback(e.getErrorCode(), space);
            if (fallback != null) {
                return fallback;
            }
            throw e;
        }

        List<GeminiRecommendOutput.CropItem> items = RecommendPromptBuilder.hasUserRequest(request)
                ? output.recommendedCrops()
                : alignToRanking(output.recommendedCrops(), candidates, estimateByCropName);

        AiRecommendation recommendation = AiRecommendation.builder()
                .space(entityManager.getReference(Space.class, space.getId()))
                .user(user)
                .preferredCrop(request.getPreferredCrop())
                .purpose(request.getPurpose())
                .additionalInfo(request.getAdditionalInfo())
                .cautionsJson(toJson(output.cautions()))
                .model(geminiClient.getModel())
                .build();

        int order = 0;
        for (GeminiRecommendOutput.CropItem item : items) {
            Crop crop = cropById.get(item.cropId());
            recommendation.addRecommendedCrop(RecommendedCrop.builder()
                    .crop(crop)
                    .cropName(crop.getName())
                    .reason(item.reason().trim())
                    .displayOrder(order++)
                    .pickType(pickType(item, estimateByCropName.containsKey(crop.getName())))
                    .build());
        }
        aiRecommendationRepository.save(recommendation);

        return new AiRecommendOutcome(toResponse(recommendation, space, estimateByCropName), false);
    }

    // 저장된 공간 면적·월세 + 표준 설비 가정값으로 계산기를 돌린다.
    // 면적이 없으면 계산할 수 없으므로 빈 순위를 돌려주고, 그때는 백과사전 후보로 추천한다.
    private List<ProfitEstimate> rankCrops(SpaceSummary space) {
        if (space.getArea() == null || space.getArea().doubleValue() <= 0) {
            return List.of();
        }
        SpaceInputs inputs = SpaceInputs.fromSpace(
                space.getArea().doubleValue(),
                space.getMonthlyRent() != null ? space.getMonthlyRent() : 0.0);
        return profitEstimateService.rank(inputs, null);
    }

    // 작물을 지정했으면 그 작물 하나만 후보로 준다.
    // 사용자 요청이 없으면 계산기 상위 3개만 후보로 준다 — 모델이 고를 여지를 없애 결과를 고정한다.
    // 요청이 있거나 계산 가능한 작물이 2개도 안 되면 백과사전 전체를 후보로 준다.
    private List<Crop> resolveCandidates(List<Crop> crops, List<ProfitEstimate> ranking,
                                         AiRecommendRequest request) {
        Map<String, Crop> cropByName = crops.stream()
                .collect(Collectors.toMap(Crop::getName, Function.identity(), (first, second) -> first));

        // 프롬프트로만 막으면 모델이 가끔 다른 작물을 끼워 넣는데, 후보를 줄이면 ID 검증에서 걸러진다.
        // 이름이 목록에 없으면(직접 입력한 옛 데이터 등) 기존 흐름을 그대로 탄다.
        if (RecommendPromptBuilder.picksSingleCrop(request)) {
            Crop preferred = cropByName.get(request.getPreferredCrop().trim());
            if (preferred != null) {
                return List.of(preferred);
            }
        }

        if (RecommendPromptBuilder.hasUserRequest(request)) {
            return crops;
        }
        List<Crop> top = ranking.stream()
                .map(estimate -> cropByName.get(estimate.cropName()))
                .filter(Objects::nonNull)
                .limit(RECOMMEND_COUNT)
                .toList();
        return top.size() >= 2 ? top : crops;
    }

    // 모델이 보낸 pickType 을 그대로 믿지 않는다. 값이 없거나 오타면 계산기 순위에 있는지로 정한다 —
    // 순위 밖 작물에 PROFIT 이 붙으면 화면이 계산기가 고른 것처럼 보여 준다.
    private String pickType(GeminiRecommendOutput.CropItem item, boolean inRanking) {
        if (RecommendPromptBuilder.PICK_PREFERENCE.equals(item.pickType())) {
            return RecommendPromptBuilder.PICK_PREFERENCE;
        }
        if (RecommendPromptBuilder.PICK_PROFIT.equals(item.pickType()) && inRanking) {
            return RecommendPromptBuilder.PICK_PROFIT;
        }
        return inRanking ? RecommendPromptBuilder.PICK_PROFIT : RecommendPromptBuilder.PICK_PREFERENCE;
    }

    // 모델이 순서를 바꾸거나 하나를 빠뜨려도 화면에는 계산기 순위가 그대로 보이게 맞춘다.
    // 빠진 작물의 근거는 계산 결과로 서버가 채운다 — 없는 근거를 모델처럼 지어내지 않기 위해서다.
    private List<GeminiRecommendOutput.CropItem> alignToRanking(
            List<GeminiRecommendOutput.CropItem> generated, List<Crop> candidates,
            Map<String, ProfitEstimate> estimateByCropName) {
        Map<Long, String> reasonByCropId = generated.stream()
                .filter(item -> item.cropId() != null && item.reason() != null && !item.reason().isBlank())
                .collect(Collectors.toMap(GeminiRecommendOutput.CropItem::cropId,
                        GeminiRecommendOutput.CropItem::reason, (first, second) -> first));

        List<GeminiRecommendOutput.CropItem> aligned = new ArrayList<>();
        for (Crop crop : candidates) {
            String reason = reasonByCropId.get(crop.getId());
            if (reason == null) {
                reason = serverReason(estimateByCropName.get(crop.getName()));
                log.warn("[AI 추천] 모델이 {} 의 근거를 빠뜨려 계산 결과로 대체했습니다.", crop.getName());
            }
            // 요청이 없을 때만 오는 경로다 — 전부 계산기 순위에서 왔다.
            aligned.add(new GeminiRecommendOutput.CropItem(
                    crop.getId(), RecommendPromptBuilder.PICK_PROFIT, reason));
        }
        return aligned;
    }

    // 모델 근거가 없을 때 쓰는 문장. 계산된 값만 옮겨 적고 판단은 넣지 않는다.
    private String serverReason(ProfitEstimate estimate) {
        if (estimate == null) {
            return "서버 계산 기준으로 이 공간에서 재배 가능한 작물입니다.";
        }
        return "서버 계산 기준 공간 제공자 예상 배분수익 월 %,d원입니다."
                .formatted(Math.round(estimate.landlordExpectedIncomeKrw()));
    }

    // Structured output을 파싱하고 의미 검증한다. 형식 또는 의미 오류 시 1회만 재시도하며,
    // 그래도 실패하면 AI_RESPONSE_INVALID. 호출 자체의 실패(타임아웃/쿼터)는 GeminiClient가 던진다.
    private GeminiRecommendOutput callStructuredAndParse(String prompt, Set<Long> validCropIds) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String text = geminiClient.generateStructured(prompt);
            try {
                GeminiRecommendOutput output = objectMapper.readValue(text, GeminiRecommendOutput.class);
                if (isValidOutput(output, validCropIds)) {
                    return output;
                }
                log.warn("[AI 추천] 파싱은 됐으나 추천 작물이 비어 있음 (attempt {}): {}", attempt, text);
            } catch (JacksonException e) {
                log.warn("[AI 추천] 최종 응답 JSON 파싱 실패 (attempt {}): {}", attempt, text);
            }
        }
        throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }

    private boolean isValidOutput(GeminiRecommendOutput output, Set<Long> validCropIds) {
        if (output == null || output.recommendedCrops() == null
                || output.recommendedCrops().size() < 2 || output.recommendedCrops().size() > RECOMMEND_COUNT
                || output.cautions() == null) {
            return false;
        }
        Set<Long> recommendedIds = output.recommendedCrops().stream()
                .map(GeminiRecommendOutput.CropItem::cropId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return recommendedIds.size() == output.recommendedCrops().size()
                && validCropIds.containsAll(recommendedIds)
                && output.recommendedCrops().stream()
                .allMatch(item -> item.reason() != null && !item.reason().isBlank());
    }

    // 후보 조회 결과 — 추천 판단에 필요한 요약 필드. null 값이 있을 수 있어 LinkedHashMap 사용(Map.of는 null 불가)
    private Map<String, Object> cropSummaryMap(Crop crop) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", crop.getId());
        map.put("name", crop.getName());
        map.put("category", crop.getCategory());
        map.put("difficulty", crop.getDifficulty().name());
        map.put("growingPeriodDays", crop.getGrowingPeriodDays());
        map.put("avgPricePerKg", crop.getAvgPricePerKg());
        return map;
    }

    private Map<String, Object> cropDetailMap(Crop crop) {
        Map<String, Object> map = cropSummaryMap(crop);
        map.put("optimalTempMin", crop.getOptimalTempMin());
        map.put("optimalTempMax", crop.getOptimalTempMax());
        map.put("optimalHumidity", crop.getOptimalHumidity());
        map.put("lightRequirement", crop.getLightRequirement() != null ? crop.getLightRequirement().name() : null);
        map.put("yieldPerSqmKg", crop.getYieldPerSqmKg());
        map.put("description", crop.getDescription());
        return map;
    }

    private String toCatalogJson(List<Crop> crops) {
        try {
            return objectMapper.writeValueAsString(crops.stream().map(this::cropDetailMap).toList());
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    // 모델이 인용할 수 있는 숫자만 골라 넘긴다. 전체 응답을 넘기면 모델이 임의 항목을 조합해
    // 서버가 계산하지 않은 값을 만들어 낸다.
    private String toServerEstimateJson(List<ProfitEstimate> ranking) {
        if (ranking.isEmpty()) {
            return "[] // 계산 가능한 작물이 없어 순위를 만들지 못했습니다. 백과사전 후보에서 2~3개를 고르세요.";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (ProfitEstimate estimate : ranking) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("cropName", estimate.cropName());
            row.put("landlordExpectedIncomeKrwPerMonth", Math.round(estimate.landlordExpectedIncomeKrw()));
            row.put("monthlyRevenueKrw", Math.round(estimate.monthlyRevenueKrw()));
            row.put("monthlyOperatingCostKrw", Math.round(estimate.monthlyOperatingCostKrw()));
            row.put("monthlySalesKg", Math.round(estimate.monthlySalesKg()));
            row.put("contractType", estimate.contractType());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    // Gemini 장애 시 같은 공간의 최근 저장 결과 재사용 — 없으면 null을 반환해 원래 예외를 그대로 던지게 한다
    private AiRecommendOutcome tryFallback(ErrorCode errorCode, SpaceSummary space) {
        if (errorCode != ErrorCode.AI_TIMEOUT && errorCode != ErrorCode.AI_QUOTA_EXCEEDED) {
            return null;
        }
        Map<String, ProfitEstimate> estimateByCropName = rankCrops(space).stream()
                .collect(Collectors.toMap(ProfitEstimate::cropName, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        return aiRecommendationRepository.findTopBySpaceIdOrderByCreatedAtDesc(space.getId())
                .map(saved -> new AiRecommendOutcome(toResponse(saved, space, estimateByCropName), true))
                .orElse(null);
    }

    // 추천 작물마다 그 작물 기준 계산값을 함께 내린다.
    // 예전에는 대표 작물 하나만 계산해, 화면 상단에 로메인이 뜨는데 수익은 상추 기준인 일이 있었다(#98).
    private AiRecommendResponse toResponse(AiRecommendation recommendation, SpaceSummary space,
                                           Map<String, ProfitEstimate> estimateByCropName) {
        List<AiRecommendResponse.RecommendedCropItem> items = recommendation.getRecommendedCrops().stream()
                .map(rc -> new AiRecommendResponse.RecommendedCropItem(
                        rc.getCropName(),
                        rc.getCrop() != null ? rc.getCrop().getId() : null,
                        rc.getReason(),
                        // 이 열이 생기기 전에 저장된 추천은 값이 없다 — 계산기 순위로 읽는다.
                        rc.getPickType() != null ? rc.getPickType() : RecommendPromptBuilder.PICK_PROFIT,
                        expectedYieldKg(rc.getCrop(), space.getArea()),
                        rc.getCrop() != null ? rc.getCrop().getAvgPricePerKg() : null,
                        toEstimateResponse(estimateByCropName.get(rc.getCropName()))
                ))
                .toList();
        // 대표 작물(첫 항목)의 계산값. 예전 응답과 자리를 맞춰 두어 화면이 한 번에 옮겨 가지 않아도 된다.
        ProfitEstimateResponse primary = items.stream()
                .map(AiRecommendResponse.RecommendedCropItem::getProfitEstimate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return AiRecommendResponse.of(recommendation, space.getId(), items,
                fromJson(recommendation.getCautionsJson()), primary);
    }

    private ProfitEstimateResponse toEstimateResponse(ProfitEstimate estimate) {
        return estimate != null ? ProfitEstimateResponse.from(estimate) : null;
    }

    // 예상 수확량(kg) = 백과사전의 ㎡당 수확량 × 공간 면적 (매칭된 작물에만 제공)
    private Integer expectedYieldKg(Crop crop, BigDecimal area) {
        if (crop == null || crop.getYieldPerSqmKg() == null || area == null) {
            return null;
        }
        return (int) Math.round(crop.getYieldPerSqmKg() * area.doubleValue());
    }

    private String toJson(List<String> cautions) {
        try {
            return objectMapper.writeValueAsString(cautions != null ? cautions : List.of());
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private List<String> fromJson(String cautionsJson) {
        if (cautionsJson == null || cautionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(cautionsJson, new TypeReference<List<String>>() {
            });
        } catch (JacksonException e) {
            return List.of(); // 저장된 이력이 깨져 있어도 응답 전체를 실패시키지 않는다
        }
    }
}
