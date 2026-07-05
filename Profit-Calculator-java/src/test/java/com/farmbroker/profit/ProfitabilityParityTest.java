package com.farmbroker.profit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitabilityParityTest {
    private static final Path DATA_DIR = Path.of("src", "main", "resources", "data");
    private static final Path EXPECTED_DIR = Path.of("src", "test", "resources", "fixtures", "expected");
    private static final List<String> GOLDEN_CROPS = List.of("LETTUCE", "BASIL", "SPROUT_GINSENG");

    @Test
    void csvSchemaContainsPythonContractHeaders() throws IOException {
        assertHeader("spaces.csv", "space_id,space_name,total_area_m2,cultivable_ratio,rack_layers,case_type,market_rent_reference_krw,source_id,data_status,remarks");
        assertHeader("crops.csv", "crop_code,crop_name,crop_category,production_unit,yield_per_m2_per_cycle,cycles_per_month,marketable_ratio,target_temperature_c,target_relative_humidity,target_ppfd_umol_m2_s,photoperiod_hours_day,water_demand_l_per_m2_day,transpiration_l_per_m2_day,source_id,data_status,reference_date,remarks");
        assertHeader("sales.csv", "sales_id,crop_code,sales_channel,price_basis,price_krw,package_quantity,sales_unit,sales_rate,platform_fee_rate,distribution_cost_rate,reference_region,reference_period,source_id,data_status,remarks");
        assertHeader("crop_materials.csv", "material_id,crop_code,material_category,material_name,quantity_basis,quantity_per_basis,material_unit,unit_price_krw,loss_rate,source_id,data_status,reference_date,remarks");
        assertHeader("packaging.csv", "package_code,crop_code,sales_channel,package_capacity,capacity_unit,package_cost_krw,source_id,data_status,reference_date,remarks");
    }

    @Test
    void csvEncodingRemovesUtf8BomAndPreservesRows() throws IOException {
        List<CsvRow> crops = CsvTableLoader.read(DATA_DIR.resolve("crops.csv"));

        Set<String> cropCodes = crops.stream().map(row -> row.get("crop_code")).collect(java.util.stream.Collectors.toSet());
        assertTrue(cropCodes.containsAll(Set.of("LETTUCE", "BASIL", "SPROUT_GINSENG")));
        assertEquals("상추", crops.get(0).get("crop_name"));
    }

    @Test
    void essentialIdentifiersExist() throws IOException {
        DataTables tables = CsvTableLoader.load(DATA_DIR);

        assertEquals(3, tables.spaces().size());
        assertTrue(tables.crops().stream().anyMatch(row -> "SPROUT_GINSENG".equals(row.get("crop_code"))));
        assertTrue(tables.profitSharing().stream().anyMatch(row -> "DEFAULT".equals(row.get("sharing_policy_id"))));
    }

    @Test
    void nineGoldenMasterCasesMatchPythonJsonExactly() throws IOException {
        ProfitabilityService service = ProfitabilityService.fromDataDirectory(DATA_DIR);

        for (int spaceId = 1; spaceId <= 3; spaceId++) {
            for (String cropCode : GOLDEN_CROPS) {
                String stem = "space_" + spaceId + "_" + cropCode.toLowerCase();
                String expected = normalizeLineEndings(Files.readString(EXPECTED_DIR.resolve(stem + "_expected.json"), StandardCharsets.UTF_8));
                String actual = normalizeLineEndings(JsonUtil.toJson(service.buildResult(spaceId, cropCode)));
                assertEquals(expected, actual, stem);
            }
        }
    }

    @Test
    void allCurrentCsvCombinationsCalculate() throws IOException {
        ProfitabilityService service = ProfitabilityService.fromDataDirectory(DATA_DIR);
        DataTables tables = CsvTableLoader.load(DATA_DIR);

        for (CsvRow space : tables.spaces()) {
            for (CsvRow crop : tables.crops()) {
                assertTrue((Boolean) service.buildResult(space.intValue("space_id"), crop.get("crop_code")).get("success"));
            }
        }
    }

    @Test
    void calculationErrorConditionsMatchPythonMessages() throws IOException {
        ProfitabilityService service = ProfitabilityService.fromDataDirectory(DATA_DIR);

        assertEquals("cultivable_ratio must be between 0 and 1.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.calculateCultivationScale(20, 1.2, 3)).getMessage());
        assertEquals("quantity_unit must be one of kg, ea, root.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.calculateMonthlyProduction(30, 2, 1.5, 0.9, "box")).getMessage());
        assertEquals("production unit kg is not compatible with sales unit root.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.calculateRevenue(10, "kg", 1, "PER_ROOT", 1200, 1, "root")).getMessage());
    }

    private static void assertHeader(String fileName, String expectedHeader) throws IOException {
        String text = Files.readString(DATA_DIR.resolve(fileName), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String firstLine = text.lines().findFirst().orElse("");
        assertEquals(expectedHeader, firstLine);
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
