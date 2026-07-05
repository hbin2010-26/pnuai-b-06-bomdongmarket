package com.farmbroker.profit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StandardAssumptions {
    final Map<String, Map<String, Double>> seasonalConditions = new LinkedHashMap<>();
    final List<CalendarProfile> calendarProfiles;
    final Map<String, ElectricityRate> electricityRatesBySeason = new LinkedHashMap<>();

    final double daysPerCropMonth;
    final double daysPerMonth;
    final double hoursPerDay;
    final double monthsPerYear;
    final double laborHoursPerSitePerWeek;
    final double laborOncostMultiplier;
    final double auxiliaryCleaningIntervalMonths;
    final double auxiliaryCleaningWaterLPerFloorM2Event;
    final double defaultLocalDistributionCostRate;
    final double referenceEquipmentCostKrw;
    final double referenceEquipmentLifetimeYears;
    final double referenceEquipmentCultivationAreaM2;
    final double contractPowerSafetyFactor;

    final double defaultCeilingHeightM;
    final double standardEnvelopeLossCoefficientWPerM3K;
    final double standardAirChangeRatePerHour;
    final double airDensityKgPerM3;
    final double airSpecificHeatJPerKgK;
    final double waterDensityKgPerM3;
    final double waterSpecificHeatJPerKgK;
    final double standardAtmosphericPressurePa;
    final double wattsPerKilowatt;
    final double joulesPerKwh;
    final double litersPerM3;

    final double defaultHeatingCop;
    final double defaultCoolingCop;
    final double defaultLedEfficacyUmolPerJ;
    final double lightingHeatGainFraction;
    final double defaultDehumidifierSecKwhPerKg;
    final double defaultHumidifierEnergyKwhPerKg;
    final double auxiliaryElectricityKwhPerM2Month;

    final double waterRateKrwPerM3;
    final double hourlyWageKrw;

    StandardAssumptions(DataTables tables) {
        Map<String, Double> environment = loadKeyValues(tables.environmentStandards(), "standard_key", "standard_value");
        Map<String, Double> equipment = loadKeyValues(tables.equipmentStandards(), "equipment_key", "equipment_value");
        Map<String, Double> policies = loadKeyValues(tables.operatingPolicies(), "policy_key", "policy_value");

        for (CsvRow row : tables.seasonalConditions()) {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("months", row.doubleValue("months_count"));
            values.put("outdoor_temperature_c", row.doubleValue("outdoor_temperature_c"));
            values.put("outdoor_relative_humidity", row.doubleValue("outdoor_relative_humidity"));
            seasonalConditions.put(row.get("climate_season"), values);
        }
        calendarProfiles = tables.calendarProfiles().stream()
                .map(row -> new CalendarProfile(
                        (int) row.doubleValue("month"),
                        row.doubleValue("days_in_month"),
                        row.get("climate_season"),
                        row.get("electricity_tariff_season")))
                .toList();
        for (CsvRow row : tables.utilityRates()) {
            if ("ENERGY".equals(row.get("rate_category"))) {
                electricityRatesBySeason.put(row.get("season"),
                        new ElectricityRate(row.doubleValue("rate_value"), row.doubleValue("base_charge_value")));
            }
        }

        daysPerCropMonth = policies.get("DAYS_PER_CROP_MONTH");
        daysPerMonth = daysPerCropMonth;
        hoursPerDay = policies.get("HOURS_PER_DAY");
        monthsPerYear = policies.get("MONTHS_PER_YEAR");
        laborHoursPerSitePerWeek = policies.get("LABOR_HOURS_PER_SITE_PER_WEEK");
        laborOncostMultiplier = policies.get("LABOR_ONCOST_MULTIPLIER");
        auxiliaryCleaningIntervalMonths = policies.get("AUXILIARY_CLEANING_INTERVAL_MONTHS");
        auxiliaryCleaningWaterLPerFloorM2Event = policies.get("AUXILIARY_CLEANING_WATER_L_PER_FLOOR_M2_EVENT");
        defaultLocalDistributionCostRate = policies.get("DEFAULT_LOCAL_DISTRIBUTION_COST_RATE");
        referenceEquipmentCostKrw = policies.get("REFERENCE_EQUIPMENT_COST_KRW");
        referenceEquipmentLifetimeYears = policies.get("REFERENCE_EQUIPMENT_LIFETIME_YEARS");
        referenceEquipmentCultivationAreaM2 = policies.get("REFERENCE_EQUIPMENT_CULTIVATION_AREA_M2");
        contractPowerSafetyFactor = policies.get("CONTRACT_POWER_SAFETY_FACTOR");

        defaultCeilingHeightM = environment.get("DEFAULT_CEILING_HEIGHT_M");
        standardEnvelopeLossCoefficientWPerM3K = environment.get("STANDARD_ENVELOPE_LOSS_COEFFICIENT_W_PER_M3K");
        standardAirChangeRatePerHour = environment.get("STANDARD_AIR_CHANGE_RATE_PER_HOUR");
        airDensityKgPerM3 = environment.get("AIR_DENSITY_KG_PER_M3");
        airSpecificHeatJPerKgK = environment.get("AIR_SPECIFIC_HEAT_J_PER_KG_K");
        waterDensityKgPerM3 = environment.get("WATER_DENSITY_KG_PER_M3");
        waterSpecificHeatJPerKgK = environment.get("WATER_SPECIFIC_HEAT_J_PER_KG_K");
        standardAtmosphericPressurePa = environment.get("STANDARD_ATMOSPHERIC_PRESSURE_PA");
        wattsPerKilowatt = environment.get("WATTS_PER_KILOWATT");
        joulesPerKwh = environment.get("JOULES_PER_KWH");
        litersPerM3 = environment.get("LITERS_PER_M3");

        defaultHeatingCop = equipment.get("DEFAULT_HEATING_COP");
        defaultCoolingCop = equipment.get("DEFAULT_COOLING_COP");
        defaultLedEfficacyUmolPerJ = equipment.get("DEFAULT_LED_EFFICACY_UMOL_PER_J");
        lightingHeatGainFraction = equipment.get("LIGHTING_HEAT_GAIN_FRACTION");
        defaultDehumidifierSecKwhPerKg = equipment.get("DEFAULT_DEHUMIDIFIER_SEC_KWH_PER_KG");
        defaultHumidifierEnergyKwhPerKg = equipment.get("DEFAULT_HUMIDIFIER_ENERGY_KWH_PER_KG");
        auxiliaryElectricityKwhPerM2Month = equipment.get("AUXILIARY_ELECTRICITY_KWH_PER_M2_MONTH");

        waterRateKrwPerM3 = rateValue(tables.utilityRates(), "TOTAL_WATER_RATE");
        hourlyWageKrw = rateValue(tables.utilityRates(), "MINIMUM_WAGE");
    }

    private static Map<String, Double> loadKeyValues(List<CsvRow> rows, String keyField, String valueField) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (CsvRow row : rows) {
            try {
                values.put(row.get(keyField), Double.parseDouble(row.get(valueField)));
            } catch (NumberFormatException ignored) {
                // Python skips descriptive non-numeric rows in key/value standard CSV files.
            }
        }
        return values;
    }

    private static double rateValue(List<CsvRow> rows, String rateCode) {
        for (CsvRow row : rows) {
            if (rateCode.equals(row.get("rate_code"))) {
                return row.doubleValue("rate_value");
            }
        }
        throw new IllegalArgumentException("No utility rate found for " + rateCode + ".");
    }

    record CalendarProfile(int month, double daysInMonth, String climateSeason, String electricityTariffSeason) {
    }

    record ElectricityRate(double rateValue, double baseChargeValue) {
    }
}

