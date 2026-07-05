package com.farmbroker.profit;

import java.util.List;

record DataTables(
        List<CsvRow> spaces,
        List<CsvRow> crops,
        List<CsvRow> sales,
        List<CsvRow> cropMaterials,
        List<CsvRow> packaging,
        List<CsvRow> operatingCosts,
        List<CsvRow> profitSharing,
        List<CsvRow> environmentStandards,
        List<CsvRow> equipmentStandards,
        List<CsvRow> utilityRates,
        List<CsvRow> operatingPolicies,
        List<CsvRow> seasonalConditions,
        List<CsvRow> calendarProfiles
) {
}

