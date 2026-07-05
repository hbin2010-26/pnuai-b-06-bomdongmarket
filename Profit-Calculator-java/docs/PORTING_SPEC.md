# Porting Spec

## Reference

- Python project: `C:\Users\user\Desktop\해커톤 프로젝트\Profit_Calculator`
- Java project: `C:\Users\user\Desktop\해커톤 프로젝트\Profit_Calcaulator_java`
- Entry point: Python `main.py`, Java `com.farmbroker.profit.ProfitCalculatorApplication`

## Scope

The Java implementation mirrors the current Python behavior. It does not improve formulas, replace assumptions, or add new costs. Python remains the source of truth.

## Flow

CSV load -> space/crop/sales/material/packaging/sharing row selection -> cultivation scale -> monthly production -> revenue -> energy/water/labor/material/distribution/depreciation -> operating cost -> profit sharing -> JSON output.

## Java Mapping

- `CsvTableLoader`: UTF-8 BOM-aware CSV loader.
- `StandardAssumptions`: Java equivalent of `config/standard_assumptions.py`.
- `ProfitabilityService`: equivalent of Python `build_result` and calculation modules.
- `JsonUtil`: dependency-free JSON writer preserving Python field order.
- `ProfitCalculatorApplication`: CLI wrapper and `output/result.json` writer.

## Runtime

Use JDK 21 explicitly. The machine PATH currently points to Java 8, so commands should set `JAVA_HOME=C:\Program Files\Java\jdk-21.0.11` or invoke that JDK directly.

