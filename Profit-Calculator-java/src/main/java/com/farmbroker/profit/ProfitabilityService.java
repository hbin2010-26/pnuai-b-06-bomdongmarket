package com.farmbroker.profit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfitabilityService {
    private final DataTables tables;
    private final StandardAssumptions assumptions;

    private ProfitabilityService(DataTables tables) {
        this.tables = tables;
        this.assumptions = new StandardAssumptions(tables);
    }

    public static ProfitabilityService fromDataDirectory(Path dataDir) throws IOException {
        return new ProfitabilityService(CsvTableLoader.load(dataDir));
    }

    public Map<String, Object> buildResult(int spaceId, String cropCode) {
        CsvRow space = findRequiredRow(tables.spaces(), "space_id", spaceId);
        CsvRow crop = findRequiredRow(tables.crops(), "crop_code", cropCode);
        CsvRow sales = findRequiredRow(tables.sales(), "crop_code", cropCode);
        List<CsvRow> materialRows = findRows(tables.cropMaterials(), "crop_code", cropCode);
        if (materialRows.isEmpty()) {
            throw new IllegalArgumentException("No material rows found for crop_code=" + cropCode + ".");
        }
        CsvRow packaging = findRequiredCompoundRow(tables.packaging(), Map.of(
                "crop_code", cropCode,
                "sales_channel", sales.get("sales_channel")));
        CsvRow sharing = findRequiredRow(tables.profitSharing(), "sharing_policy_id", "DEFAULT");

        Map<String, Object> scale = calculateCultivationScale(
                space.doubleValue("total_area_m2"),
                space.doubleValue("cultivable_ratio"),
                space.intValue("rack_layers"));
        Map<String, Object> production = calculateMonthlyProduction(
                d(scale, "cultivation_area_m2"),
                crop.doubleValue("yield_per_m2_per_cycle"),
                crop.doubleValue("cycles_per_month"),
                crop.doubleValue("marketable_ratio"),
                crop.get("production_unit"));
        Map<String, Object> revenue = calculateRevenue(
                d(production, "marketable_quantity"),
                s(production, "quantity_unit"),
                sales.doubleValue("sales_rate"),
                sales.get("price_basis"),
                sales.doubleValue("price_krw"),
                sales.doubleValue("package_quantity"),
                sales.get("sales_unit"));
        Map<String, Object> operatingCost = calculateOperatingCost(
                space.doubleValue("total_area_m2"),
                d(scale, "cultivation_area_m2"),
                crop.doubleValue("cycles_per_month"),
                crop.doubleValue("target_temperature_c"),
                crop.doubleValue("target_relative_humidity"),
                crop.doubleValue("target_ppfd_umol_m2_s"),
                crop.doubleValue("photoperiod_hours_day"),
                crop.doubleValue("water_demand_l_per_m2_day"),
                crop.doubleValue("transpiration_l_per_m2_day"),
                d(revenue, "expected_revenue"),
                d(revenue, "expected_sales_quantity"),
                materialRows,
                packaging,
                sales.doubleValue("distribution_cost_rate"),
                sales.doubleValue("platform_fee_rate"));
        Map<String, Object> profit = calculateProfit(
                d(revenue, "expected_revenue"),
                d(operatingCost, "operating_cost_before_depreciation"),
                sharing.doubleValue("owner_share_rate"),
                sharing.doubleValue("headquarters_share_rate"));

        Map<String, Object> result = map();
        result.put("success", true);
        result.put("message", "공실 수익성 계산이 완료되었습니다.");
        Map<String, Object> data = map();
        result.put("data", data);
        data.put("predictionId", null);
        data.put("spaceId", space.intValue("space_id"));
        data.put("cropCode", crop.get("crop_code"));
        data.put("cropName", crop.get("crop_name"));
        data.put("cropType", crop.get("crop_name"));
        data.put("calculationPeriod", "AVERAGE_MONTHLY_FROM_ANNUAL_SCENARIO");
        data.put("spaceVolumeM3", normalize(d(m(operatingCost, "energy"), "space_volume_m3")));
        data.put("usableFloorAreaM2", normalize(d(scale, "usable_floor_area_m2")));
        data.put("cultivationAreaM2", normalize(d(scale, "cultivation_area_m2")));
        data.put("production", normalizeNumbers(map(
                "grossQuantity", production.get("gross_quantity"),
                "marketableQuantity", production.get("marketable_quantity"),
                "expectedSalesQuantity", revenue.get("expected_sales_quantity"),
                "quantityUnit", production.get("quantity_unit"),
                "unitPriceKrw", revenue.get("unit_price_krw"))));
        data.put("grossYieldKg", normalize(production.get("gross_yield_kg")));
        data.put("expectedYieldKg", normalize(production.get("marketable_yield_kg")));
        data.put("expectedSalesKg", normalize(revenue.get("expected_sales_kg")));
        data.put("expectedRevenue", normalize(revenue.get("expected_revenue")));
        data.put("costBreakdown", map(
                "electricityCost", normalize(operatingCost.get("electricity_cost")),
                "waterCost", normalize(operatingCost.get("water_cost")),
                "materialCost", normalize(operatingCost.get("material_cost")),
                "laborCost", normalize(operatingCost.get("labor_cost")),
                "distributionCost", normalize(operatingCost.get("distribution_cost")),
                "maintenanceCost", normalize(operatingCost.get("maintenance_cost"))));
        data.put("operatingCostBreakdown", buildOperatingCostBreakdown(operatingCost));
        data.put("expectedCost", normalize(operatingCost.get("operating_cost_before_depreciation")));
        data.put("operatingCostBeforeDepreciation", normalize(operatingCost.get("operating_cost_before_depreciation")));
        data.put("depreciationCost", normalize(operatingCost.get("depreciation_cost")));
        data.put("totalExpectedCostAfterDepreciation", normalize(operatingCost.get("total_expected_cost_after_depreciation")));
        data.put("expectedProfit", normalize(profit.get("expected_profit")));
        data.put("operatingProfitBeforeDepreciation", normalize(d(revenue, "expected_revenue") - d(operatingCost, "operating_cost_before_depreciation")));
        data.put("projectedProfitAfterDepreciation", normalize(d(revenue, "expected_revenue") - d(operatingCost, "total_expected_cost_after_depreciation")));
        data.put("profitDistribution", map(
                "ownerShareRate", sharing.doubleValue("owner_share_rate"),
                "ownerShareAmount", normalize(profit.get("owner_share_amount")),
                "headquartersShareRate", sharing.doubleValue("headquarters_share_rate"),
                "headquartersShareAmount", normalize(profit.get("headquarters_share_amount")),
                "undistributedLoss", normalize(profit.get("undistributed_loss"))));
        data.put("breakEvenMonth", null);
        data.put("summary", crop.get("crop_name") + " 재배 기준 월 공실 수익성 계산 결과입니다.");
        return result;
    }

    DataTables tables() {
        return tables;
    }

    Map<String, Object> calculateCultivationScale(double totalAreaM2, double cultivableRatio, int rackLayers) {
        requireNonNegative("total_area_m2", totalAreaM2);
        requireRatio("cultivable_ratio", cultivableRatio);
        if (rackLayers < 1) {
            throw new IllegalArgumentException("rack_layers must be greater than or equal to 1.");
        }
        double usableFloorAreaM2 = totalAreaM2 * cultivableRatio;
        double cultivationAreaM2 = usableFloorAreaM2 * rackLayers;
        return map("usable_floor_area_m2", usableFloorAreaM2, "cultivation_area_m2", cultivationAreaM2);
    }

    Map<String, Object> calculateMonthlyProduction(double cultivationAreaM2, double yieldPerM2PerCycle,
                                                   double cyclesPerMonth, double marketableRatio, String quantityUnit) {
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        requireNonNegative("yield_per_m2_per_cycle", yieldPerM2PerCycle);
        if (cyclesPerMonth <= 0) {
            throw new IllegalArgumentException("cycles_per_month must be greater than 0.");
        }
        requireRatio("marketable_ratio", marketableRatio);
        if (!quantityUnit.equals("kg") && !quantityUnit.equals("ea") && !quantityUnit.equals("root")) {
            throw new IllegalArgumentException("quantity_unit must be one of kg, ea, root.");
        }
        double grossQuantity = cultivationAreaM2 * yieldPerM2PerCycle * cyclesPerMonth;
        double marketableQuantity = grossQuantity * marketableRatio;
        Map<String, Object> result = map(
                "gross_quantity", grossQuantity,
                "marketable_quantity", marketableQuantity,
                "quantity_unit", quantityUnit);
        if ("kg".equals(quantityUnit)) {
            result.put("gross_yield_kg", grossQuantity);
            result.put("marketable_yield_kg", marketableQuantity);
        }
        return result;
    }

    Map<String, Object> calculateRevenue(double marketableQuantity, String quantityUnit, double salesRate,
                                         String priceBasis, double priceKrw, double packageQuantity, String salesUnit) {
        requireNonNegative("marketable_quantity", marketableQuantity);
        requireRatio("sales_rate", salesRate);
        requireNonNegative("price_krw", priceKrw);
        if (packageQuantity <= 0) {
            throw new IllegalArgumentException("package_quantity must be greater than 0.");
        }
        double unitPriceKrw;
        if ("PER_PACKAGE".equals(priceBasis)) {
            validateUnitCompatibility(quantityUnit, salesUnit);
            unitPriceKrw = priceKrw / packageQuantity;
        } else if ("PER_KG".equals(priceBasis) || "PER_UNIT".equals(priceBasis) || "PER_ROOT".equals(priceBasis)) {
            String expectedUnit = switch (priceBasis) {
                case "PER_KG" -> "kg";
                case "PER_UNIT" -> "ea";
                case "PER_ROOT" -> "root";
                default -> throw new IllegalStateException();
            };
            validateUnitCompatibility(quantityUnit, expectedUnit);
            validateUnitCompatibility(quantityUnit, salesUnit);
            unitPriceKrw = priceKrw;
        } else {
            throw new IllegalArgumentException("Unsupported price_basis.");
        }
        double expectedSalesQuantity = marketableQuantity * salesRate;
        double expectedRevenue = expectedSalesQuantity * unitPriceKrw;
        Map<String, Object> result = map(
                "expected_sales_quantity", expectedSalesQuantity,
                "expected_revenue", expectedRevenue,
                "unit_price_krw", unitPriceKrw,
                "quantity_unit", quantityUnit);
        if ("kg".equals(quantityUnit)) {
            result.put("expected_sales_kg", expectedSalesQuantity);
        }
        return result;
    }

    Map<String, Object> calculateOperatingCost(double totalAreaM2, double cultivationAreaM2, double cyclesPerMonth,
                                               double targetTemperatureC, double targetRelativeHumidity,
                                               double targetPpfdUmolM2S, double photoperiodHoursDay,
                                               double waterDemandLPerM2Day, double transpirationLPerM2Day,
                                               double expectedRevenue, double expectedSalesQuantity,
                                               List<CsvRow> materialRows, CsvRow packagingRow,
                                               Double distributionCostRate, double platformFeeRate) {
        Map<String, Object> energy = calculateEnergyCost(totalAreaM2, cultivationAreaM2, targetTemperatureC,
                targetRelativeHumidity, targetPpfdUmolM2S, photoperiodHoursDay, transpirationLPerM2Day);
        Map<String, Object> water = calculateWaterCost(totalAreaM2, cultivationAreaM2, waterDemandLPerM2Day,
                d(energy, "average_monthly_humidification_water_m3"));
        Map<String, Object> labor = calculateLaborCost();
        Map<String, Object> materials = calculateMaterialCost(cultivationAreaM2, cyclesPerMonth,
                expectedSalesQuantity, materialRows, packagingRow);
        Map<String, Object> distribution = calculateDistributionCost(expectedRevenue, distributionCostRate, platformFeeRate);
        Map<String, Object> depreciation = calculateDepreciationCost(cultivationAreaM2);

        double beforeDepreciation = d(energy, "electricity_cost_krw") + d(water, "water_cost_krw")
                + d(labor, "labor_cost_krw") + d(materials, "material_cost_krw")
                + d(distribution, "distribution_cost");
        double afterDepreciation = beforeDepreciation + d(depreciation, "monthly_depreciation_cost");
        return map(
                "energy", energy,
                "water", water,
                "labor", labor,
                "materials", materials,
                "distribution", distribution,
                "depreciation", depreciation,
                "electricity_cost", d(energy, "electricity_cost_krw"),
                "water_cost", d(water, "water_cost_krw"),
                "labor_cost", d(labor, "labor_cost_krw"),
                "material_cost", d(materials, "material_cost_krw"),
                "distribution_cost", d(distribution, "distribution_cost"),
                "maintenance_cost", 0,
                "operating_cost_before_depreciation", beforeDepreciation,
                "depreciation_cost", d(depreciation, "monthly_depreciation_cost"),
                "total_expected_cost_after_depreciation", afterDepreciation,
                "expected_cost", beforeDepreciation);
    }

    private Map<String, Object> calculateEnergyCost(double totalAreaM2, double cultivationAreaM2,
                                                    double targetTemperatureC, double targetRelativeHumidity,
                                                    double targetPpfdUmolM2S, double photoperiodHoursDay,
                                                    double transpirationLPerM2Day) {
        double spaceVolumeM3 = calculateSpaceVolume(totalAreaM2);
        Map<String, Object> seasonal = map();
        double annualElectricityKwh = 0.0;
        double annualElectricityEnergyChargeKrw = 0.0;
        double annualTemperatureControlKwh = 0.0;
        double annualLightingKwh = 0.0;
        double annualAuxiliaryKwh = 0.0;
        double annualHumidityControlKwh = 0.0;
        double annualHumidificationWaterM3 = 0.0;
        double estimatedPeakPowerKw = 0.0;

        for (StandardAssumptions.CalendarProfile calendar : assumptions.calendarProfiles) {
            String seasonName = calendar.climateSeason();
            String tariffSeason = calendar.electricityTariffSeason();
            double days = calendar.daysInMonth();
            Map<String, Double> condition = assumptions.seasonalConditions.get(seasonName);

            Map<String, Object> lighting = calculateLightingEnergy(cultivationAreaM2, targetPpfdUmolM2S, photoperiodHoursDay, days);
            Map<String, Object> auxiliary = calculateAuxiliaryEnergyForDays(cultivationAreaM2, days);
            Map<String, Object> temperature = calculateTemperatureControlEnergy(spaceVolumeM3, targetTemperatureC,
                    condition.get("outdoor_temperature_c"), d(lighting, "lighting_heat_gain_w"), photoperiodHoursDay, days);
            Map<String, Object> humidity = calculateHumidityControlEnergy(spaceVolumeM3, cultivationAreaM2,
                    targetTemperatureC, condition.get("outdoor_temperature_c"), targetRelativeHumidity,
                    condition.get("outdoor_relative_humidity"), transpirationLPerM2Day, days);

            double monthlyTotalKwh = d(temperature, "temperature_control_energy_kwh")
                    + d(lighting, "monthly_lighting_energy_kwh")
                    + d(humidity, "humidity_control_energy_kwh")
                    + d(auxiliary, "auxiliary_energy_kwh");
            double tariffRate = assumptions.electricityRatesBySeason.get(tariffSeason).rateValue();
            annualElectricityKwh += monthlyTotalKwh;
            annualElectricityEnergyChargeKrw += monthlyTotalKwh * tariffRate;
            annualTemperatureControlKwh += d(temperature, "temperature_control_energy_kwh");
            annualLightingKwh += d(lighting, "monthly_lighting_energy_kwh");
            annualHumidityControlKwh += d(humidity, "humidity_control_energy_kwh");
            annualAuxiliaryKwh += d(auxiliary, "auxiliary_energy_kwh");
            annualHumidificationWaterM3 += d(humidity, "humidification_water_m3");

            double peakPowerKw = (d(lighting, "lighting_power_w") + Math.max(
                    d(temperature, "net_heating_load_lit_w") / assumptions.defaultHeatingCop,
                    d(temperature, "net_cooling_load_lit_w") / assumptions.defaultCoolingCop)) / assumptions.wattsPerKilowatt;
            estimatedPeakPowerKw = Math.max(estimatedPeakPowerKw, peakPowerKw);

            String key = seasonName.toLowerCase();
            @SuppressWarnings("unchecked")
            Map<String, Object> seasonResult = (Map<String, Object>) seasonal.get(key);
            if (seasonResult == null) {
                seasonResult = map(
                        "monthly_electricity_kwh", 0.0,
                        "months_count", 0,
                        "temperature_control_kwh", 0.0,
                        "humidity_control_kwh", 0.0,
                        "humidification_water_m3", 0.0,
                        "temperature_control", temperature,
                        "humidity_control", humidity);
                seasonal.put(key, seasonResult);
            }
            seasonResult.put("monthly_electricity_kwh", d(seasonResult, "monthly_electricity_kwh") + monthlyTotalKwh);
            seasonResult.put("months_count", ((Integer) seasonResult.get("months_count")) + 1);
            seasonResult.put("temperature_control_kwh", d(seasonResult, "temperature_control_kwh") + d(temperature, "temperature_control_energy_kwh"));
            seasonResult.put("humidity_control_kwh", d(seasonResult, "humidity_control_kwh") + d(humidity, "humidity_control_energy_kwh"));
            seasonResult.put("humidification_water_m3", d(seasonResult, "humidification_water_m3") + d(humidity, "humidification_water_m3"));
        }

        for (Object value : seasonal.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> seasonResult = (Map<String, Object>) value;
            int monthsCount = (Integer) seasonResult.get("months_count");
            seasonResult.put("monthly_electricity_kwh", d(seasonResult, "monthly_electricity_kwh") / monthsCount);
            seasonResult.put("temperature_control_kwh", d(seasonResult, "temperature_control_kwh") / monthsCount);
            seasonResult.put("humidity_control_kwh", d(seasonResult, "humidity_control_kwh") / monthsCount);
            seasonResult.put("humidification_water_m3", d(seasonResult, "humidification_water_m3") / monthsCount);
        }

        double averageMonthlyElectricityKwh = annualElectricityKwh / assumptions.monthsPerYear;
        double estimatedContractPowerKw = estimatedPeakPowerKw * assumptions.contractPowerSafetyFactor;
        double monthlyBasicChargeKrw = estimatedContractPowerKw
                * assumptions.electricityRatesBySeason.get("SHOULDER").baseChargeValue();
        double electricityCostKrw = annualElectricityEnergyChargeKrw / assumptions.monthsPerYear + monthlyBasicChargeKrw;
        return map(
                "space_volume_m3", spaceVolumeM3,
                "seasonal", seasonal,
                "temperature_control_kwh", annualTemperatureControlKwh / assumptions.monthsPerYear,
                "lighting_kwh", annualLightingKwh / assumptions.monthsPerYear,
                "humidity_control_kwh", annualHumidityControlKwh / assumptions.monthsPerYear,
                "auxiliary_kwh", annualAuxiliaryKwh / assumptions.monthsPerYear,
                "annual_electricity_kwh", annualElectricityKwh,
                "average_monthly_electricity_kwh", averageMonthlyElectricityKwh,
                "annual_energy_charge_krw", annualElectricityEnergyChargeKrw,
                "monthly_basic_charge_krw", monthlyBasicChargeKrw,
                "estimated_peak_power_kw", estimatedPeakPowerKw,
                "estimated_contract_power_kw", estimatedContractPowerKw,
                "average_monthly_humidification_water_m3", annualHumidificationWaterM3 / assumptions.monthsPerYear,
                "electricity_cost_krw", electricityCostKrw,
                "lighting", calculateLightingEnergy(cultivationAreaM2, targetPpfdUmolM2S, photoperiodHoursDay, assumptions.daysPerCropMonth));
    }

    private double calculateSpaceVolume(double totalAreaM2) {
        requireNonNegative("total_area_m2", totalAreaM2);
        if (assumptions.defaultCeilingHeightM <= 0) {
            throw new IllegalArgumentException("ceiling_height_m must be greater than 0.");
        }
        return totalAreaM2 * assumptions.defaultCeilingHeightM;
    }

    private Map<String, Object> calculateLightingEnergy(double cultivationAreaM2, double targetPpfdUmolM2S,
                                                        double photoperiodHoursDay, double days) {
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        requireNonNegative("target_ppfd_umol_m2_s", targetPpfdUmolM2S);
        if (photoperiodHoursDay < 0 || photoperiodHoursDay > assumptions.hoursPerDay) {
            throw new IllegalArgumentException("photoperiod_hours_day must be between 0 and 24.");
        }
        double lightingPowerW = targetPpfdUmolM2S * cultivationAreaM2 / assumptions.defaultLedEfficacyUmolPerJ;
        double monthlyLightingEnergyKwh = lightingPowerW * photoperiodHoursDay * days / assumptions.wattsPerKilowatt;
        double lightingHeatGainW = lightingPowerW * assumptions.lightingHeatGainFraction;
        return map("lighting_power_w", lightingPowerW,
                "monthly_lighting_energy_kwh", monthlyLightingEnergyKwh,
                "lighting_heat_gain_w", lightingHeatGainW);
    }

    private Map<String, Object> calculateBaseThermalLoad(double spaceVolumeM3, double targetTemperatureC,
                                                         double outdoorTemperatureC) {
        requireNonNegative("space_volume_m3", spaceVolumeM3);
        double temperatureDifferenceK = Math.abs(targetTemperatureC - outdoorTemperatureC);
        double envelopeHeatLoadW = assumptions.standardEnvelopeLossCoefficientWPerM3K * spaceVolumeM3 * temperatureDifferenceK;
        double ventilationHeatLoadW = assumptions.airDensityKgPerM3 * assumptions.airSpecificHeatJPerKgK
                * spaceVolumeM3 * assumptions.standardAirChangeRatePerHour * temperatureDifferenceK / 3600;
        double baseThermalLoadW = envelopeHeatLoadW + ventilationHeatLoadW;
        return map("temperature_difference_k", temperatureDifferenceK,
                "envelope_heat_load_w", envelopeHeatLoadW,
                "ventilation_heat_load_w", ventilationHeatLoadW,
                "base_thermal_load_w", baseThermalLoadW);
    }

    private Map<String, Object> calculateTemperatureControlEnergy(double spaceVolumeM3, double targetTemperatureC,
                                                                  double outdoorTemperatureC, double lightingHeatGainW,
                                                                  double photoperiodHoursDay, double days) {
        Map<String, Object> baseLoad = calculateBaseThermalLoad(spaceVolumeM3, targetTemperatureC, outdoorTemperatureC);
        requireNonNegative("lighting_heat_gain_w", lightingHeatGainW);
        double lightingHoursMonth = photoperiodHoursDay * days;
        double darkHoursMonth = (assumptions.hoursPerDay - photoperiodHoursDay) * days;
        double baseThermalLoadW = d(baseLoad, "base_thermal_load_w");
        double heatingEnergyKwh = 0.0;
        double coolingEnergyKwh = 0.0;
        double netHeatingLoadLitW = 0.0;
        double netCoolingLoadLitW = 0.0;
        if (targetTemperatureC > outdoorTemperatureC) {
            netHeatingLoadLitW = Math.max(baseThermalLoadW - lightingHeatGainW, 0);
            heatingEnergyKwh = ((netHeatingLoadLitW * lightingHoursMonth + baseThermalLoadW * darkHoursMonth)
                    / assumptions.defaultHeatingCop / assumptions.wattsPerKilowatt);
        } else if (targetTemperatureC < outdoorTemperatureC) {
            netCoolingLoadLitW = baseThermalLoadW + lightingHeatGainW;
            coolingEnergyKwh = ((netCoolingLoadLitW * lightingHoursMonth + baseThermalLoadW * darkHoursMonth)
                    / assumptions.defaultCoolingCop / assumptions.wattsPerKilowatt);
        }
        double airMassKg = assumptions.airDensityKgPerM3 * spaceVolumeM3;
        double initialSensibleHeatKwh = airMassKg * assumptions.airSpecificHeatJPerKgK
                * d(baseLoad, "temperature_difference_k") / assumptions.joulesPerKwh;
        Map<String, Object> result = map();
        result.putAll(baseLoad);
        result.put("net_heating_load_lit_w", netHeatingLoadLitW);
        result.put("net_cooling_load_lit_w", netCoolingLoadLitW);
        result.put("heating_energy_kwh", heatingEnergyKwh);
        result.put("cooling_energy_kwh", coolingEnergyKwh);
        result.put("temperature_control_energy_kwh", heatingEnergyKwh + coolingEnergyKwh);
        result.put("initial_sensible_heat_kwh_reference", initialSensibleHeatKwh);
        return result;
    }

    private double calculateSaturationVaporPressurePa(double temperatureC) {
        return 610.94 * Math.exp((17.625 * temperatureC) / (temperatureC + 243.04));
    }

    private double calculateHumidityRatio(double temperatureC, double relativeHumidity) {
        requireRatio("relative_humidity", relativeHumidity);
        double saturationPressurePa = calculateSaturationVaporPressurePa(temperatureC);
        double vaporPressurePa = relativeHumidity * saturationPressurePa;
        return 0.622 * vaporPressurePa / (assumptions.standardAtmosphericPressurePa - vaporPressurePa);
    }

    private Map<String, Object> calculateHumidityControlEnergy(double spaceVolumeM3, double cultivationAreaM2,
                                                               double targetTemperatureC, double outdoorTemperatureC,
                                                               double targetRelativeHumidity, double outdoorRelativeHumidity,
                                                               double transpirationLPerM2Day, double days) {
        requireNonNegative("space_volume_m3", spaceVolumeM3);
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        requireRatio("target_relative_humidity", targetRelativeHumidity);
        requireRatio("outdoor_relative_humidity", outdoorRelativeHumidity);
        requireNonNegative("transpiration_l_per_m2_day", transpirationLPerM2Day);
        double targetHumidityRatio = calculateHumidityRatio(targetTemperatureC, targetRelativeHumidity);
        double outdoorHumidityRatio = calculateHumidityRatio(outdoorTemperatureC, outdoorRelativeHumidity);
        double exchangedAirVolumeM3Month = spaceVolumeM3 * assumptions.standardAirChangeRatePerHour
                * assumptions.hoursPerDay * days;
        double dryAirMassKg = exchangedAirVolumeM3Month * assumptions.airDensityKgPerM3;
        double exchangeWaterKg = Math.abs(targetHumidityRatio - outdoorHumidityRatio) * dryAirMassKg;
        double transpirationWaterKg = cultivationAreaM2 * transpirationLPerM2Day * days;
        double suppliedWaterKg = targetHumidityRatio > outdoorHumidityRatio ? exchangeWaterKg : 0.0;
        double removedWaterKg = transpirationWaterKg;
        if (targetHumidityRatio < outdoorHumidityRatio) {
            removedWaterKg += exchangeWaterKg;
        }
        double dehumidificationEnergyKwh = removedWaterKg * assumptions.defaultDehumidifierSecKwhPerKg;
        double humidificationEnergyKwh = suppliedWaterKg * assumptions.defaultHumidifierEnergyKwhPerKg;
        double humidificationWaterM3 = suppliedWaterKg / assumptions.waterDensityKgPerM3;
        return map("removed_water_kg", removedWaterKg,
                "supplied_water_kg", suppliedWaterKg,
                "target_humidity_ratio", targetHumidityRatio,
                "outdoor_humidity_ratio", outdoorHumidityRatio,
                "humidification_water_m3", humidificationWaterM3,
                "dehumidification_energy_kwh", dehumidificationEnergyKwh,
                "humidification_energy_kwh", humidificationEnergyKwh,
                "humidity_control_energy_kwh", dehumidificationEnergyKwh + humidificationEnergyKwh);
    }

    private Map<String, Object> calculateAuxiliaryEnergy(double cultivationAreaM2) {
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        return map("auxiliary_energy_kwh", cultivationAreaM2 * assumptions.auxiliaryElectricityKwhPerM2Month);
    }

    private Map<String, Object> calculateAuxiliaryEnergyForDays(double cultivationAreaM2, double days) {
        return map("auxiliary_energy_kwh", d(calculateAuxiliaryEnergy(cultivationAreaM2), "auxiliary_energy_kwh")
                * days / assumptions.daysPerCropMonth);
    }

    private Map<String, Object> calculateWaterCost(double totalAreaM2, double cultivationAreaM2,
                                                   double waterDemandLPerM2Day, double humidificationWaterM3) {
        Map<String, Object> irrigation = calculateCropIrrigationWater(cultivationAreaM2, waterDemandLPerM2Day);
        requireNonNegative("humidification_water_m3", humidificationWaterM3);
        Map<String, Object> auxiliary = calculateAuxiliaryCleaningWater(totalAreaM2);
        double totalWaterM3 = d(irrigation, "crop_irrigation_water_m3") + humidificationWaterM3
                + d(auxiliary, "auxiliary_cleaning_water_m3");
        double waterCostKrw = totalWaterM3 * assumptions.waterRateKrwPerM3;
        Map<String, Object> result = map();
        result.putAll(irrigation);
        result.put("humidification_water_m3", humidificationWaterM3);
        result.putAll(auxiliary);
        result.put("auxiliary_water_m3", d(auxiliary, "auxiliary_cleaning_water_m3"));
        result.put("total_water_m3", totalWaterM3);
        result.put("water_cost_krw", waterCostKrw);
        return result;
    }

    private Map<String, Object> calculateCropIrrigationWater(double cultivationAreaM2, double waterDemandLPerM2Day) {
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        requireNonNegative("water_demand_l_per_m2_day", waterDemandLPerM2Day);
        double cropIrrigationWaterL = cultivationAreaM2 * waterDemandLPerM2Day * assumptions.daysPerMonth;
        double cropIrrigationWaterM3 = cropIrrigationWaterL / assumptions.litersPerM3;
        return map("crop_irrigation_water_l", cropIrrigationWaterL,
                "crop_irrigation_water_m3", cropIrrigationWaterM3);
    }

    private Map<String, Object> calculateAuxiliaryCleaningWater(double totalAreaM2) {
        requireNonNegative("total_area_m2", totalAreaM2);
        double auxiliaryCleaningWaterM3 = totalAreaM2 * assumptions.auxiliaryCleaningWaterLPerFloorM2Event
                / assumptions.auxiliaryCleaningIntervalMonths / assumptions.litersPerM3;
        return map("auxiliary_cleaning_water_m3", auxiliaryCleaningWaterM3);
    }

    private Map<String, Object> calculateLaborCost() {
        double monthlyLaborHours = assumptions.laborHoursPerSitePerWeek * 52 / assumptions.monthsPerYear;
        double laborCostKrw = monthlyLaborHours * assumptions.hourlyWageKrw * assumptions.laborOncostMultiplier;
        return map("weekly_hours_per_site", assumptions.laborHoursPerSitePerWeek,
                "monthly_labor_hours", monthlyLaborHours,
                "hourly_wage_krw", assumptions.hourlyWageKrw,
                "labor_oncost_multiplier", assumptions.laborOncostMultiplier,
                "labor_cost_krw", laborCostKrw);
    }

    private Map<String, Object> calculateMaterialCost(double cultivationAreaM2, double cyclesPerMonth,
                                                      double expectedSalesQuantity, List<CsvRow> materialRows,
                                                      CsvRow packagingRow) {
        Map<String, Object> itemCosts = calculateMaterialItems(cultivationAreaM2, cyclesPerMonth,
                expectedSalesQuantity, materialRows);
        Map<String, Object> packaging = calculatePackagingCost(expectedSalesQuantity, packagingRow);
        double totalMaterialCost = d(itemCosts, "planting_material_cost") + d(itemCosts, "nutrient_cost")
                + d(itemCosts, "growing_medium_cost") + d(itemCosts, "consumable_cost")
                + d(packaging, "packaging_cost");
        Map<String, Object> result = map();
        result.putAll(itemCosts);
        result.putAll(packaging);
        result.put("material_cost_krw", totalMaterialCost);
        result.put("total_material_cost", totalMaterialCost);
        return result;
    }

    private Map<String, Object> calculateMaterialItems(double cultivationAreaM2, double cyclesPerMonth,
                                                       double expectedSalesQuantity, List<CsvRow> materialRows) {
        requireNonNegative("cultivation_area_m2", cultivationAreaM2);
        requireNonNegative("expected_sales_quantity", expectedSalesQuantity);
        if (cyclesPerMonth <= 0) {
            throw new IllegalArgumentException("cycles_per_month must be greater than 0.");
        }
        Map<String, Object> costs = map(
                "planting_material_cost", 0.0,
                "nutrient_cost", 0.0,
                "growing_medium_cost", 0.0,
                "consumable_cost", 0.0);
        for (CsvRow row : materialRows) {
            double quantityPerBasis = row.doubleValue("quantity_per_basis");
            double unitPriceKrw = row.doubleValue("unit_price_krw");
            double lossRate = row.doubleValue("loss_rate");
            double quantity;
            switch (row.get("quantity_basis")) {
                case "PER_M2_CYCLE" -> quantity = cultivationAreaM2 * cyclesPerMonth * quantityPerBasis;
                case "PER_PRODUCTION_UNIT" -> quantity = expectedSalesQuantity * quantityPerBasis;
                case "PER_MONTH", "FIXED_PER_SITE_MONTH" -> quantity = quantityPerBasis;
                default -> throw new IllegalArgumentException("Unsupported quantity_basis: " + row.get("quantity_basis"));
            }
            double cost = quantity * unitPriceKrw * (1 + lossRate);
            String categoryKey = switch (row.get("material_category")) {
                case "PLANTING_MATERIAL" -> "planting_material_cost";
                case "NUTRIENT" -> "nutrient_cost";
                case "GROWING_MEDIUM" -> "growing_medium_cost";
                case "CONSUMABLE" -> "consumable_cost";
                default -> throw new IllegalArgumentException("Unsupported material_category: " + row.get("material_category"));
            };
            costs.put(categoryKey, d(costs, categoryKey) + cost);
        }
        return costs;
    }

    private Map<String, Object> calculatePackagingCost(double expectedSalesQuantity, CsvRow packagingRow) {
        requireNonNegative("expected_sales_quantity", expectedSalesQuantity);
        double packageCapacity = packagingRow.doubleValue("package_capacity");
        double packageCostKrw = packagingRow.doubleValue("package_cost_krw");
        if (packageCapacity <= 0) {
            throw new IllegalArgumentException("package_capacity must be greater than 0.");
        }
        int requiredPackages = (int) Math.ceil(expectedSalesQuantity / packageCapacity);
        double packagingCost = requiredPackages * packageCostKrw;
        return map("required_packages", requiredPackages,
                "package_capacity", packageCapacity,
                "packaging_cost", packagingCost);
    }

    private Map<String, Object> calculateDistributionCost(double expectedRevenue, Double distributionCostRate,
                                                          double platformFeeRate) {
        if (expectedRevenue < 0) {
            throw new IllegalArgumentException("expected_revenue must be greater than or equal to 0.");
        }
        double rate = distributionCostRate == null ? assumptions.defaultLocalDistributionCostRate : distributionCostRate;
        if (rate < 0 || platformFeeRate < 0) {
            throw new IllegalArgumentException("cost rates must be greater than or equal to 0.");
        }
        double localDistributionCost = expectedRevenue * rate;
        double platformFeeCost = expectedRevenue * platformFeeRate;
        return map("distribution_cost_rate", rate,
                "platform_fee_rate", platformFeeRate,
                "local_distribution_cost", localDistributionCost,
                "platform_fee_cost", platformFeeCost,
                "distribution_cost", localDistributionCost + platformFeeCost);
    }

    private Map<String, Object> calculateDepreciationCost(double cultivationAreaM2) {
        if (cultivationAreaM2 < 0) {
            throw new IllegalArgumentException("cultivation_area_m2 must be greater than or equal to 0.");
        }
        double referenceMonthlyDepreciation = assumptions.referenceEquipmentCostKrw
                / (assumptions.referenceEquipmentLifetimeYears * assumptions.monthsPerYear);
        double monthlyDepreciationCost = referenceMonthlyDepreciation * cultivationAreaM2
                / assumptions.referenceEquipmentCultivationAreaM2;
        return map("reference_equipment_cost_krw", assumptions.referenceEquipmentCostKrw,
                "reference_area_m2", assumptions.referenceEquipmentCultivationAreaM2,
                "lifetime_years", assumptions.referenceEquipmentLifetimeYears,
                "reference_monthly_depreciation", referenceMonthlyDepreciation,
                "monthly_depreciation_cost", monthlyDepreciationCost);
    }

    Map<String, Object> calculateProfit(double expectedRevenue, double expectedCost,
                                        double ownerShareRate, double headquartersShareRate) {
        requireNonNegative("expected_revenue", expectedRevenue);
        requireNonNegative("expected_cost", expectedCost);
        requireRatio("owner_share_rate", ownerShareRate);
        requireRatio("headquarters_share_rate", headquartersShareRate);
        if (Math.abs((ownerShareRate + headquartersShareRate) - 1.0) > 0.000001) {
            throw new IllegalArgumentException("owner_share_rate and headquarters_share_rate must add up to 1.0.");
        }
        double expectedProfit = expectedRevenue - expectedCost;
        double ownerShareAmount;
        double headquartersShareAmount;
        double undistributedLoss;
        if (expectedProfit > 0) {
            ownerShareAmount = expectedProfit * ownerShareRate;
            headquartersShareAmount = expectedProfit * headquartersShareRate;
            undistributedLoss = 0;
        } else {
            ownerShareAmount = 0;
            headquartersShareAmount = 0;
            undistributedLoss = expectedProfit;
        }
        return map("expected_profit", expectedProfit,
                "owner_share_amount", ownerShareAmount,
                "headquarters_share_amount", headquartersShareAmount,
                "undistributed_loss", undistributedLoss);
    }

    private Map<String, Object> buildOperatingCostBreakdown(Map<String, Object> operatingCost) {
        Map<String, Object> energy = m(operatingCost, "energy");
        Map<String, Object> seasonal = m(energy, "seasonal");
        Map<String, Object> water = m(operatingCost, "water");
        Map<String, Object> labor = m(operatingCost, "labor");
        Map<String, Object> materials = m(operatingCost, "materials");
        Map<String, Object> distribution = m(operatingCost, "distribution");
        Map<String, Object> depreciation = m(operatingCost, "depreciation");
        return normalizeNumbers(map(
                "energy", map(
                        "seasonal", map(
                                "winterMonthlyKwh", d(m(seasonal, "winter"), "monthly_electricity_kwh"),
                                "summerMonthlyKwh", d(m(seasonal, "summer"), "monthly_electricity_kwh"),
                                "shoulderMonthlyKwh", d(m(seasonal, "shoulder"), "monthly_electricity_kwh")),
                        "temperatureControlKwh", energy.get("temperature_control_kwh"),
                        "lightingKwh", energy.get("lighting_kwh"),
                        "humidityControlKwh", energy.get("humidity_control_kwh"),
                        "auxiliaryKwh", energy.get("auxiliary_kwh"),
                        "annualElectricityKwh", energy.get("annual_electricity_kwh"),
                        "averageMonthlyElectricityKwh", energy.get("average_monthly_electricity_kwh"),
                        "electricityCost", energy.get("electricity_cost_krw"),
                        "lightingPowerW", d(m(energy, "lighting"), "lighting_power_w"),
                        "monthlyBasicCharge", energy.get("monthly_basic_charge_krw"),
                        "estimatedContractPowerKw", energy.get("estimated_contract_power_kw")),
                "water", map(
                        "cropIrrigationWaterM3", water.get("crop_irrigation_water_m3"),
                        "humidificationWaterM3", water.get("humidification_water_m3"),
                        "auxiliaryCleaningWaterM3", water.get("auxiliary_cleaning_water_m3"),
                        "totalWaterM3", water.get("total_water_m3"),
                        "waterCost", water.get("water_cost_krw")),
                "labor", map(
                        "weeklyHoursPerSite", labor.get("weekly_hours_per_site"),
                        "monthlyHours", labor.get("monthly_labor_hours"),
                        "hourlyWage", labor.get("hourly_wage_krw"),
                        "laborOncostMultiplier", labor.get("labor_oncost_multiplier"),
                        "laborCost", labor.get("labor_cost_krw")),
                "materials", map(
                        "plantingMaterialCost", materials.get("planting_material_cost"),
                        "nutrientCost", materials.get("nutrient_cost"),
                        "growingMediumCost", materials.get("growing_medium_cost"),
                        "consumableCost", materials.get("consumable_cost"),
                        "packagingCost", materials.get("packaging_cost"),
                        "requiredPackages", materials.get("required_packages"),
                        "totalMaterialCost", materials.get("material_cost_krw")),
                "distribution", map(
                        "distributionRate", distribution.get("distribution_cost_rate"),
                        "platformFeeRate", distribution.get("platform_fee_rate"),
                        "localDistributionCost", distribution.get("local_distribution_cost"),
                        "platformFeeCost", distribution.get("platform_fee_cost"),
                        "distributionCost", distribution.get("distribution_cost")),
                "operatingCostBeforeDepreciation", operatingCost.get("operating_cost_before_depreciation"),
                "depreciation", map(
                        "referenceEquipmentCost", depreciation.get("reference_equipment_cost_krw"),
                        "referenceAreaM2", depreciation.get("reference_area_m2"),
                        "lifetimeYears", depreciation.get("lifetime_years"),
                        "monthlyDepreciationCost", depreciation.get("monthly_depreciation_cost")),
                "totalExpectedCostAfterDepreciation", operatingCost.get("total_expected_cost_after_depreciation")));
    }

    private static CsvRow findRequiredRow(List<CsvRow> rows, String fieldName, Object expectedValue) {
        String expectedText = String.valueOf(expectedValue);
        for (CsvRow row : rows) {
            if (expectedText.equals(row.get(fieldName))) {
                return row;
            }
        }
        throw new IllegalArgumentException("No row found for " + fieldName + "=" + expectedText + ".");
    }

    private static List<CsvRow> findRows(List<CsvRow> rows, String fieldName, Object expectedValue) {
        String expectedText = String.valueOf(expectedValue);
        return rows.stream().filter(row -> expectedText.equals(row.get(fieldName))).toList();
    }

    private static CsvRow findRequiredCompoundRow(List<CsvRow> rows, Map<String, Object> conditions) {
        for (CsvRow row : rows) {
            boolean matches = true;
            for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                if (!String.valueOf(condition.getValue()).equals(row.get(condition.getKey()))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return row;
            }
        }
        StringBuilder conditionText = new StringBuilder();
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            if (!conditionText.isEmpty()) {
                conditionText.append(", ");
            }
            conditionText.append(entry.getKey()).append("=").append(entry.getValue());
        }
        throw new IllegalArgumentException("No row found for " + conditionText + ".");
    }

    private static void validateUnitCompatibility(String quantityUnit, String salesUnit) {
        if (!quantityUnit.equals(salesUnit)) {
            throw new IllegalArgumentException("production unit " + quantityUnit
                    + " is not compatible with sales unit " + salesUnit + ".");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0.");
        }
    }

    private static void requireRatio(String name, double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be between 0 and 1.");
        }
    }

    static Object normalize(Object value) {
        if (value instanceof Double doubleValue) {
            double rounded = Math.rint(doubleValue * 1_000_000.0) / 1_000_000.0;
            if (rounded == 0.0) {
                rounded = 0.0;
            }
            if (Math.rint(rounded) == rounded) {
                return (long) rounded;
            }
            return rounded;
        }
        if (value instanceof Float floatValue) {
            return normalize(floatValue.doubleValue());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeNumbers(Map<String, Object> value) {
        Map<String, Object> normalized = map();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            Object item = entry.getValue();
            if (item instanceof Map<?, ?> mapValue) {
                normalized.put(entry.getKey(), normalizeNumbers((Map<String, Object>) mapValue));
            } else if (item instanceof List<?>) {
                normalized.put(entry.getKey(), item);
            } else {
                normalized.put(entry.getKey(), normalize(item));
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> m(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    private static double d(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(key + " is not numeric.");
    }

    private static String s(Map<String, Object> map, String key) {
        return String.valueOf(map.get(key));
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}

