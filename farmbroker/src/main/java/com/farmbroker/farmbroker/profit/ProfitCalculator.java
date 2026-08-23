package com.farmbroker.farmbroker.profit;

import com.farmbroker.farmbroker.profit.ProfitReferenceData.CropProduction;
import com.farmbroker.farmbroker.profit.ProfitReferenceData.MonthlyEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// hbin Profit_Calculator 1.0.1(Python)의 계산 블록 1~10을 자바로 포팅한 결정론적 계산기.
// LLM 도구(function calling)가 아니라 서버가 추천 작물명으로 직접 호출한다.
// 기간 기준: 월평균(전력은 12개월 시나리오의 산술평균). Python 원본과 수치가 일치하도록 계산 순서를 유지한다.
@Component
@RequiredArgsConstructor
public class ProfitCalculator {

    private static final double DAYS_PER_AVERAGE_MONTH = 365.0 / 12.0;
    private static final double HOURS_PER_AVERAGE_MONTH = 24.0 * DAYS_PER_AVERAGE_MONTH;
    // 양액량만 30일·1.1배를 쓴다(Python 1.0.1 그대로).
    private static final double NUTRIENT_DAYS_PER_MONTH = 30.0;
    private static final double NUTRIENT_DRAINAGE_ALLOWANCE = 1.1;

    // 물리·요율 상수(전기요율, LED 효율, 월별 외기 등)는 여전히 CSV 참조 데이터에서 온다.
    private final ProfitReferenceData data;
    // 작물별 재배 파라미터는 DB 에서 온다 — 자료를 보완할 때 코드를 고치지 않도록 분리했다.
    private final CropProductionProvider crops;

    public boolean supports(String cropName) {
        return crops.hasCultivationData(cropName);
    }

    public List<String> supportedCrops() {
        return crops.supportedCropNames();
    }

    // 공간 입력 + 작물명 + 판매 단가로 월평균 수익성을 계산한다.
    // 단가는 계산기가 직접 들고 있지 않고 MarketPriceProvider가 조회한 값을 주입받는다 —
    // 시세 출처(백과사전/KAMIS)가 바뀌어도 계산 로직은 그대로 두기 위함.
    // 재배 파라미터가 없는 작물이면 예외 대신 호출 전 supports()로 걸러야 한다.
    public ProfitEstimate estimate(SpaceInputs space, String cropName, MarketPrice price) {
        CropProduction crop = crops.cropProduction(cropName);
        double pricePerKg = price.pricePerKgKrw();

        Space s = calculateSpace(space, crop);
        Production production = calculateProduction(s, crop);
        double revenue = production.monthlySalesKg() * pricePerKg;               // 블록3 매출

        Lighting lighting = calculateLighting(s, crop);
        Energy energy = calculateEnergy(s, crop, lighting);                      // 블록4~6 전력
        double electricityCost = energy.averageMonthlyEnergyKwh() * data.standard("electricity_rate_krw_kwh");

        double waterCost = calculateWaterCost(s, crop);                          // 블록7 용수
        MaterialCost material = calculateMaterialCost(s, crop);                  // 블록8 재료
        double laborCost = calculateLaborCost(production, crop);                 // 블록9 인건

        return calculateProfit(space, cropName, s, production, price, revenue,
                lighting, energy, electricityCost, waterCost, material, laborCost); // 블록10 손익
    }

    // 블록1: 공간 면적·체적. 다단 층 수는 작물에서 온다(1.0.1).
    private Space calculateSpace(SpaceInputs in, CropProduction crop) {
        if (in.totalAreaM2() <= 0) {
            throw new IllegalArgumentException("공실 전체면적은 0보다 커야 합니다.");
        }
        if (in.cultivableRatio() < 0 || in.cultivableRatio() > 1) {
            throw new IllegalArgumentException("재배가능 비율은 0과 1 사이여야 합니다.");
        }
        if (crop.moduleLayers() <= 0 || in.ceilingHeightM() <= 0) {
            throw new IllegalArgumentException("재배모듈 층 수와 천장 높이는 0보다 커야 합니다.");
        }
        double availableFloor = in.totalAreaM2() * in.cultivableRatio();
        double cultivation = availableFloor * crop.moduleLayers();
        double volume = in.totalAreaM2() * in.ceilingHeightM();
        double length = Math.sqrt(in.totalAreaM2());
        double wallOneSide = length * in.ceilingHeightM();
        return new Space(in.totalAreaM2(), crop.moduleLayers(), availableFloor, cultivation,
                volume, wallOneSide);
    }

    // 블록2: 생산량
    private Production calculateProduction(Space s, CropProduction crop) {
        if (crop.yieldPerCycleKgM2() < 0 || crop.cyclesPerMonth() < 0) {
            throw new IllegalArgumentException("생산량과 회전수는 음수가 될 수 없습니다.");
        }
        if (crop.marketableRate() <= 0 || crop.marketableRate() > 1) {
            throw new IllegalArgumentException("상품화율은 0보다 크고 1 이하여야 합니다.");
        }
        double perM2Month = crop.yieldPerCycleKgM2() * crop.cyclesPerMonth();
        double total = s.cultivationAreaM2() * perM2Month;
        double sales = total * crop.marketableRate();
        return new Production(total, sales);
    }

    // 블록4: 조명 정격/에너지/발열 (월 무관)
    private Lighting calculateLighting(Space s, CropProduction crop) {
        double ledEfficiency = data.electricStandard("led_photon_efficiency_umol_j");
        double heatConversion = data.electricStandard("heat_conversion_rate");
        double lightingHoursDay = crop.lightingHoursDay();
        if (lightingHoursDay < 0 || lightingHoursDay > 24) {
            throw new IllegalArgumentException("하루 조명 점등시간은 0~24시간이어야 합니다.");
        }
        if (ledEfficiency <= 0) {
            throw new IllegalArgumentException("LED 효율 입력값을 확인해 주세요.");
        }
        double powerW = s.cultivationAreaM2() * crop.requiredPpfdUmolM2S() / ledEfficiency;
        double onHours = lightingHoursDay * DAYS_PER_AVERAGE_MONTH;
        double offHours = (24.0 - lightingHoursDay) * DAYS_PER_AVERAGE_MONTH;
        double energyKwh = powerW * onHours / 1000.0;
        double heatW = powerW * heatConversion;
        return new Lighting(powerW, heatW, onHours, offHours, energyKwh);
    }

    // 블록4~6: 월별 냉난방·가습/제습을 계산하고 조명까지 더해 월평균 전력량을 구한다
    private Energy calculateEnergy(Space s, CropProduction crop, Lighting lighting) {
        double wallU = data.electricStandard("wall_u_value_w_m2_k");
        double airDensity = data.standard("air_density_kg_m3");
        double airSpecificHeat = data.standard("air_specific_heat_j_kg_k");
        double ach = data.standard("air_changes_per_hour");
        double cop = data.standard("hvac_cop");
        double shr = data.standard("sensible_heat_ratio");
        if (cop <= 0 || shr <= 0 || shr > 1) {
            throw new IllegalArgumentException("COP, SHR 입력값을 확인해 주세요.");
        }

        double atmospheric = data.standard("atmospheric_pressure_pa");
        double dryAirGasConstant = data.standard("dry_air_gas_constant_j_kg_k");
        double humidityRatioConstant = data.standard("humidity_ratio_constant");
        double latentHeat = data.standard("latent_heat_kwh_kg");
        double dehumidificationSec = data.standard("dehumidification_sec_kwh_kg");
        double humidificationSec = data.standard("humidification_sec_kwh_kg");

        double targetTemp = crop.targetTemperatureC();
        if (crop.targetRelativeHumidity() < 0 || crop.targetRelativeHumidity() > 1) {
            throw new IllegalArgumentException("목표 상대습도는 0과 1 사이여야 합니다.");
        }
        double targetHumidityRatio = humidityRatio(targetTemp, crop.targetRelativeHumidity(),
                atmospheric, humidityRatioConstant).ratio();
        double monthlyEvapotranspirationKg =
                s.cultivationAreaM2() * crop.dailyEvapotranspirationMm() * DAYS_PER_AVERAGE_MONTH;

        double energySum = 0.0;
        List<MonthlyEnvironment> months = data.monthlyEnvironment();
        for (MonthlyEnvironment env : months) {
            double delta = targetTemp - env.outdoorTemperatureC();

            // 외부 노출 벽면 2면 가정 (블록4)
            double wallLoadW = delta * s.wallAreaOneSideM2() * wallU * 2.0;
            double ventilationLoadW = delta * s.volumeM3() * airDensity * airSpecificHeat * ach / 3600.0;
            double maintainLoadW = wallLoadW + ventilationLoadW;

            double heatOnW = Math.max(maintainLoadW - lighting.heatW(), 0.0);
            double coolOnW = Math.max(lighting.heatW() - maintainLoadW, 0.0);
            double heatOffW = Math.max(maintainLoadW, 0.0);
            double coolOffW = Math.max(-maintainLoadW, 0.0);

            double heatingKwh = (heatOnW * lighting.onHours() + heatOffW * lighting.offHours()) / (cop * 1000.0);
            double coolingKwh = (coolOnW * lighting.onHours() + coolOffW * lighting.offHours()) / (shr * cop * 1000.0);
            double sensibleCoolingKwh = coolingKwh * shr * cop;

            // 블록5: 가습/제습
            HumidityRatio outside = humidityRatio(env.outdoorTemperatureC(), env.outdoorRelativeHumidity(),
                    atmospheric, humidityRatioConstant);
            double dryAirDensity = (atmospheric - outside.vaporPressure())
                    / (dryAirGasConstant * (env.outdoorTemperatureC() + 273.15));
            double monthlyDryAirMass = s.volumeM3() * ach * dryAirDensity * HOURS_PER_AVERAGE_MONTH;
            double ventilationMoistureKg = monthlyDryAirMass * (outside.ratio() - targetHumidityRatio);
            double baseNetMoistureKg = monthlyEvapotranspirationKg + ventilationMoistureKg;

            double latentCoolingKwh = sensibleCoolingKwh * (1.0 - shr) / shr;
            double coolingDehumidificationKg = latentCoolingKwh / latentHeat;
            double remainingMoistureKg = baseNetMoistureKg - coolingDehumidificationKg;

            double dehumidificationKwh = Math.max(0.0, remainingMoistureKg) * dehumidificationSec;
            double humidificationKwh = Math.max(0.0, -remainingMoistureKg) * humidificationSec;

            // 블록6: 조명 포함 월 총 환경제어 전력량
            double monthTotal = lighting.energyKwh() + heatingKwh + coolingKwh
                    + dehumidificationKwh + humidificationKwh;
            energySum += monthTotal;
        }

        double averageMonthlyEnergy = energySum / months.size();
        return new Energy(monthlyEvapotranspirationKg, averageMonthlyEnergy);
    }

    // 블록7: 용수비. 배액률은 배액량/작물 관수량이라, 증발산량을 (1 - 배액률)로 나눠
    // 배액까지 포함한 관수량을 구한다(1.0.1).
    private double calculateWaterCost(Space s, CropProduction crop) {
        double drainageRatio = data.standard("drainage_ratio");
        if (drainageRatio < 0 || drainageRatio >= 1) {
            throw new IllegalArgumentException("배액률은 0 이상 1 미만이어야 합니다.");
        }
        double evapotranspirationL = s.cultivationAreaM2() * crop.dailyEvapotranspirationMm() * DAYS_PER_AVERAGE_MONTH;
        double irrigationL = evapotranspirationL / (1.0 - drainageRatio);
        double otherWaterL = s.totalAreaM2() * data.standard("other_water_l_m2_day") * DAYS_PER_AVERAGE_MONTH;
        double totalWaterM3 = (irrigationL + otherWaterL) / 1000.0;
        return totalWaterM3 * data.standard("water_rate_krw_m3");
    }

    // 블록8: 재료비 = 월 모종비 + 월 양액비 (1.0.1).
    // 모종비는 1회 단가가 아니라 월 환산 단가라 회전수를 곱하지 않는다.
    // 양액량의 1.1 배와 30 일은 Python 원본을 그대로 따른다 — 수도비 쪽 배액률(0.3)·
    // 월 길이(365/12)와 기준이 다른데, 원본을 바꾸지 않고 옮기는 것이 먼저다(#128 리뷰에 남김).
    private MaterialCost calculateMaterialCost(Space s, CropProduction crop) {
        double seedling = s.cultivationAreaM2() * crop.seedlingCostPerM2MonthKrw();
        double nutrientSolutionL = s.cultivationAreaM2() * crop.dailyEvapotranspirationMm()
                * NUTRIENT_DAYS_PER_MONTH * NUTRIENT_DRAINAGE_ALLOWANCE;
        double nutrientCost = nutrientSolutionL * data.standard("nutrient_cost_per_l_krw");
        return new MaterialCost(seedling, nutrientSolutionL, nutrientCost, seedling + nutrientCost);
    }

    // 블록9: 인건비 (판매량을 상품화율로 역산한 전체 생산량 기준)
    private double calculateLaborCost(Production production, CropProduction crop) {
        if (crop.marketableRate() <= 0) {
            throw new IllegalArgumentException("상품화율은 0보다 커야 합니다.");
        }
        double reconstructedProduction = production.monthlySalesKg() / crop.marketableRate();
        double laborHours = reconstructedProduction * data.standard("labor_hours_per_kg");
        return laborHours * data.standard("minimum_wage_krw_hour");
    }

    // 블록10: 손익·수익배분·계약형태 추천
    private ProfitEstimate calculateProfit(SpaceInputs in, String cropName, Space s, Production production,
                                           MarketPrice price, double revenue, Lighting lighting, Energy energy,
                                           double electricityCost, double waterCost, MaterialCost material,
                                           double laborCost) {
        double otherCost = data.standard("other_cost_krw_month");
        double equipmentRentalRate = data.standard("equipment_rental_cost_krw_m2_month");
        if (equipmentRentalRate < 0) {
            throw new IllegalArgumentException("면적당 월 기기 대여비는 음수가 될 수 없습니다.");
        }
        double landlordRatio = data.contraction("landlord_share_ratio");
        if (landlordRatio < 0 || landlordRatio > 1) {
            throw new IllegalArgumentException("공간 대여자 배분비율은 0과 1 사이여야 합니다.");
        }
        if (in.desiredMonthlyRentKrw() < 0) {
            throw new IllegalArgumentException("원하는 월세는 음수가 될 수 없습니다.");
        }

        // 설비는 사 두는 게 아니라 빌려 쓰는 것으로 잡는다 — 바닥면적 기준 월 대여비(1.0.1).
        double equipmentRentalCost = s.availableFloorAreaM2() * equipmentRentalRate;
        double baseCost = electricityCost + waterCost + material.totalKrw() + equipmentRentalCost;
        double operatingCost = baseCost + laborCost + otherCost;
        double operatingProfit = revenue - operatingCost;
        double landlordExpectedIncome = operatingProfit * landlordRatio;
        double businessOperatingProfit = operatingProfit - landlordExpectedIncome;
        double rentIncomeDifference = landlordExpectedIncome - in.desiredMonthlyRentKrw();

        boolean operatingLoss = operatingProfit < 0;
        boolean longTermRecommended = !operatingLoss && landlordExpectedIncome >= in.desiredMonthlyRentKrw();
        String recommendation = longTermRecommended
                ? "도심형 대량생산 스마트팜 방식 추천" : "개인취미 대여 방식 추천";
        String contractType = longTermRecommended ? "장기계약형" : "단기계약형";

        return new ProfitEstimate(
                cropName,
                s.totalAreaM2(), in.cultivableRatio(), s.moduleLayers(), in.ceilingHeightM(),
                s.availableFloorAreaM2(), s.cultivationAreaM2(),
                lighting.powerW(), energy.averageMonthlyEnergyKwh(),
                production.monthlyTotalProductionKg(), production.monthlySalesKg(), price, revenue,
                electricityCost, waterCost,
                material.seedlingKrw(), material.nutrientSolutionL(), material.nutrientKrw(),
                material.totalKrw(),
                laborCost, equipmentRentalCost, otherCost, operatingCost,
                operatingProfit, landlordRatio, landlordExpectedIncome, in.desiredMonthlyRentKrw(),
                rentIncomeDifference, businessOperatingProfit, operatingLoss, longTermRecommended,
                recommendation, contractType);
    }

    // 마그누스 근사식 포화수증기압 → 습공기비 + 수증기압
    private HumidityRatio humidityRatio(double temperatureC, double relativeHumidity,
                                        double atmosphericPressurePa, double humidityRatioConstant) {
        double saturation = 610.94 * Math.exp(17.625 * temperatureC / (temperatureC + 243.04));
        double vapor = relativeHumidity * saturation;
        double ratio = humidityRatioConstant * vapor / (atmosphericPressurePa - vapor);
        return new HumidityRatio(ratio, vapor);
    }

    // ── 내부 중간 계산 결과 ──
    private record Space(double totalAreaM2, double moduleLayers, double availableFloorAreaM2,
                         double cultivationAreaM2, double volumeM3, double wallAreaOneSideM2) {
    }

    private record Production(double monthlyTotalProductionKg, double monthlySalesKg) {
    }

    private record Lighting(double powerW, double heatW, double onHours, double offHours, double energyKwh) {
    }

    private record Energy(double monthlyEvapotranspirationKg, double averageMonthlyEnergyKwh) {
    }

    private record MaterialCost(double seedlingKrw, double nutrientSolutionL,
                                double nutrientKrw, double totalKrw) {
    }

    private record HumidityRatio(double ratio, double vaporPressure) {
    }
}
