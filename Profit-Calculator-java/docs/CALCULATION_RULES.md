# Calculation Rules

## Cultivation Scale

`usable_floor_area_m2 = total_area_m2 * cultivable_ratio`

`cultivation_area_m2 = usable_floor_area_m2 * rack_layers`

## Production

`gross_quantity = cultivation_area_m2 * yield_per_m2_per_cycle * cycles_per_month`

`marketable_quantity = gross_quantity * marketable_ratio`

Allowed production units are exactly `kg`, `ea`, and `root`. `grossYieldKg`, `expectedYieldKg`, and `expectedSalesKg` are populated only for `kg`.

## Revenue

`expected_sales_quantity = marketable_quantity * sales_rate`

For `PER_PACKAGE`, `unit_price_krw = price_krw / package_quantity`. For `PER_KG`, `PER_UNIT`, and `PER_ROOT`, `unit_price_krw = price_krw`.

Production and sales units must match, otherwise calculation fails.

## Operating Cost

`operating_cost_before_depreciation = electricity_cost + water_cost + labor_cost + material_cost + distribution_cost`

`total_expected_cost_after_depreciation = operating_cost_before_depreciation + depreciation_cost`

Labor is fixed per site. Maintenance cost is always `0`, matching Python.

## Profit

`expected_profit = expected_revenue - operating_cost_before_depreciation`

Positive profit is split by owner/headquarters rates. Negative profit is not distributed and is stored as `undistributedLoss`.

## Rounding

Python calculates with `float`, then recursively normalizes JSON numbers with `round(value, 6)`. Integral rounded floats are emitted as integers. Java mirrors this with `double` and six-decimal output normalization.

