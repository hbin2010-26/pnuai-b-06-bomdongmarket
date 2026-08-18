package com.farmbroker.farmbroker.profit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 수익 계산기가 쓰는 작물별 재배 파라미터.
//
// 원래 crop_production_info.csv 에 3종만 들어 있었다. CSV 는 값을 바꾸려면 코드를 고쳐 다시
// 배포해야 해서, 자료 조사가 진행되는 동안 작물을 늘리기 어렵다. 이 테이블로 옮겨 행만
// 추가하면 계산 대상 작물이 늘어난다.
//
// sourceId·dataStatus·referenceDate·remarks 는 원본 계산기 프로젝트의 CSV 스키마를 그대로
// 따른다. 실측값과 추정값이 섞이기 때문에 값마다 근거를 함께 두어야 나중에 어느 숫자를
// 믿을 수 있는지 판단할 수 있다.
@Entity
@Table(name = "crop_cultivation_params")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CropCultivationParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작물 백과사전(crops.name)과 같은 한글 이름으로 맞춘다 — 계산기가 작물명으로 조회한다.
    @Column(nullable = false, unique = true, length = 50)
    private String cropName;

    @Column(nullable = false)
    private Double yieldPerCycleKgM2;

    @Column(nullable = false)
    private Double cyclesPerMonth;

    @Column(nullable = false)
    private Double marketableRate;

    @Column(nullable = false)
    private Double requiredPpfdUmolM2S;

    @Column(nullable = false)
    private Double lightingHoursDay;

    @Column(nullable = false)
    private Double targetTemperatureC;

    @Column(nullable = false)
    private Double targetRelativeHumidity;

    @Column(nullable = false)
    private Double dailyEvapotranspirationMm;

    @Column(nullable = false)
    private Double materialCostPerM2CycleKrw;

    @Column(nullable = false)
    private Double otherMaterialCostMonthKrw;

    // ── 근거 ──
    // 어느 자료에서 온 값인지 가리키는 식별자(원본 프로젝트의 source_id 규칙을 따른다).
    @Column(length = 100)
    private String sourceId;

    // MVP_ESTIMATE(추정) / MEASURED(실측) 등. 화면에 실측인지 추정인지 알리는 데 쓴다.
    @Column(nullable = false, length = 30)
    private String dataStatus;

    // 그 자료의 기준일. 오래된 값인지 판단할 수 있어야 한다.
    private LocalDate referenceDate;

    @Column(length = 500)
    private String remarks;

    @Builder
    private CropCultivationParam(String cropName, Double yieldPerCycleKgM2, Double cyclesPerMonth,
                                 Double marketableRate, Double requiredPpfdUmolM2S, Double lightingHoursDay,
                                 Double targetTemperatureC, Double targetRelativeHumidity,
                                 Double dailyEvapotranspirationMm, Double materialCostPerM2CycleKrw,
                                 Double otherMaterialCostMonthKrw, String sourceId, String dataStatus,
                                 LocalDate referenceDate, String remarks) {
        this.cropName = cropName;
        this.yieldPerCycleKgM2 = yieldPerCycleKgM2;
        this.cyclesPerMonth = cyclesPerMonth;
        this.marketableRate = marketableRate;
        this.requiredPpfdUmolM2S = requiredPpfdUmolM2S;
        this.lightingHoursDay = lightingHoursDay;
        this.targetTemperatureC = targetTemperatureC;
        this.targetRelativeHumidity = targetRelativeHumidity;
        this.dailyEvapotranspirationMm = dailyEvapotranspirationMm;
        this.materialCostPerM2CycleKrw = materialCostPerM2CycleKrw;
        this.otherMaterialCostMonthKrw = otherMaterialCostMonthKrw;
        this.sourceId = sourceId;
        this.dataStatus = dataStatus;
        this.referenceDate = referenceDate;
        this.remarks = remarks;
    }
}
