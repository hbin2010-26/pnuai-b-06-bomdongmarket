# CSV Schema

All CSV files are read as UTF-8 with optional BOM removal, equivalent to Python `utf-8-sig`.

## Loaded by Main Flow

- `spaces.csv`: `space_id,space_name,total_area_m2,cultivable_ratio,rack_layers,case_type,market_rent_reference_krw,source_id,data_status,remarks`
- `crops.csv`: `crop_code,crop_name,crop_category,production_unit,yield_per_m2_per_cycle,cycles_per_month,marketable_ratio,target_temperature_c,target_relative_humidity,target_ppfd_umol_m2_s,photoperiod_hours_day,water_demand_l_per_m2_day,transpiration_l_per_m2_day,source_id,data_status,reference_date,remarks`
- `sales.csv`: `sales_id,crop_code,sales_channel,price_basis,price_krw,package_quantity,sales_unit,sales_rate,platform_fee_rate,distribution_cost_rate,reference_region,reference_period,source_id,data_status,remarks`
- `crop_materials.csv`: `material_id,crop_code,material_category,material_name,quantity_basis,quantity_per_basis,material_unit,unit_price_krw,loss_rate,source_id,data_status,reference_date,remarks`
- `packaging.csv`: `package_code,crop_code,sales_channel,package_capacity,capacity_unit,package_cost_krw,source_id,data_status,reference_date,remarks`
- `profit_sharing.csv`: `sharing_policy_id,owner_share_rate,headquarters_share_rate`

## Standard Assumption CSVs

- `environment_standards.csv`
- `equipment_standards.csv`
- `utility_rates.csv`
- `operating_policies.csv`
- `seasonal_conditions.csv`
- `calendar_profiles.csv`

`operating_costs.csv` is copied and schema-tested for parity with the Python data directory, but the current Python calculation path does not use it.

## Current Row Counts

- spaces: 3
- crops: 11
- sales: 11
- crop_materials: 36
- packaging: 11
- profit_sharing: 1

