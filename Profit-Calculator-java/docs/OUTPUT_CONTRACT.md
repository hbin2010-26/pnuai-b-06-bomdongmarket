# Output Contract

Java emits the same logical JSON contract as Python `build_result`.

Top-level fields:

- `success`
- `message`
- `data`

Main `data` fields:

- `predictionId`
- `spaceId`
- `cropCode`
- `cropName`
- `cropType`
- `calculationPeriod`
- `spaceVolumeM3`
- `usableFloorAreaM2`
- `cultivationAreaM2`
- `production`
- `grossYieldKg`
- `expectedYieldKg`
- `expectedSalesKg`
- `expectedRevenue`
- `costBreakdown`
- `operatingCostBreakdown`
- `expectedCost`
- `operatingCostBeforeDepreciation`
- `depreciationCost`
- `totalExpectedCostAfterDepreciation`
- `expectedProfit`
- `operatingProfitBeforeDepreciation`
- `projectedProfitAfterDepreciation`
- `profitDistribution`
- `breakEvenMonth`
- `summary`

Java writes `output/result.json` inside the Java project. Field order is kept aligned with Python for human-readable diffs. Tests normalize only line endings before comparing golden master JSON.

