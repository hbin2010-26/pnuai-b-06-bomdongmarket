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

    // 1.0.1 부터 다단 층 수가 작물 속성이다 — 상추 4단, 딸기 2단처럼 작물마다 다르다.
    @Column(nullable = false)
    private Double moduleLayers;

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

    // 1회 단가가 아니라 월 환산 단가다. 양액비는 증발산량에서 계산하므로 여기 두지 않는다.
    @Column(nullable = false)
    private Double seedlingCostPerM2MonthKrw;

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
    private CropCultivationParam(String cropName, Double moduleLayers, Double yieldPerCycleKgM2,
                                 Double cyclesPerMonth,
                                 Double marketableRate, Double requiredPpfdUmolM2S, Double lightingHoursDay,
                                 Double targetTemperatureC, Double targetRelativeHumidity,
                                 Double dailyEvapotranspirationMm, Double seedlingCostPerM2MonthKrw,
                                 String sourceId, String dataStatus,
                                 LocalDate referenceDate, String remarks) {
        this.cropName = cropName;
        this.moduleLayers = moduleLayers;
        this.yieldPerCycleKgM2 = yieldPerCycleKgM2;
        this.cyclesPerMonth = cyclesPerMonth;
        this.marketableRate = marketableRate;
        this.requiredPpfdUmolM2S = requiredPpfdUmolM2S;
        this.lightingHoursDay = lightingHoursDay;
        this.targetTemperatureC = targetTemperatureC;
        this.targetRelativeHumidity = targetRelativeHumidity;
        this.dailyEvapotranspirationMm = dailyEvapotranspirationMm;
        this.seedlingCostPerM2MonthKrw = seedlingCostPerM2MonthKrw;
        this.sourceId = sourceId;
        this.dataStatus = dataStatus;
        this.referenceDate = referenceDate;
        this.remarks = remarks;
    }

    // 사람이 조사해 넣은 값인지. 시드는 이 값이 false 인 행만 갱신한다 —
    // 조사값을 배포마다 추정값으로 되돌리면 조사한 사람이 같은 일을 다시 해야 한다.
    public boolean isSeedEstimate() {
        return SEED_DATA_STATUS.equals(dataStatus);
    }

    // 이 값이 시드에서 온 추정값이라는 표시. Initializer 와 같은 문자열을 봐야 해서 여기 둔다.
    public static final String SEED_DATA_STATUS = "MVP_ESTIMATE";

    // 같은 작물의 값을 시드 기준으로 다시 채운다.
    // 새 엔티티를 만들어 그 값을 옮겨 담는 식이라, 파라미터가 늘어도 고칠 곳이 여기 한 군데다.
    public void replaceValuesFrom(CropCultivationParam seed) {
        this.moduleLayers = seed.moduleLayers;
        this.yieldPerCycleKgM2 = seed.yieldPerCycleKgM2;
        this.cyclesPerMonth = seed.cyclesPerMonth;
        this.marketableRate = seed.marketableRate;
        this.requiredPpfdUmolM2S = seed.requiredPpfdUmolM2S;
        this.lightingHoursDay = seed.lightingHoursDay;
        this.targetTemperatureC = seed.targetTemperatureC;
        this.targetRelativeHumidity = seed.targetRelativeHumidity;
        this.dailyEvapotranspirationMm = seed.dailyEvapotranspirationMm;
        this.seedlingCostPerM2MonthKrw = seed.seedlingCostPerM2MonthKrw;
        this.sourceId = seed.sourceId;
        this.dataStatus = seed.dataStatus;
        this.referenceDate = seed.referenceDate;
        this.remarks = seed.remarks;
    }
}
