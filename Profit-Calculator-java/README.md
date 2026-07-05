# Profit Calculator Java Parity Port

This project is a Java parity implementation of the Python reference calculator at:

`C:\Users\user\Desktop\해커톤 프로젝트\Profit_Calculator`

It preserves the Python formulas, CSV inputs, units, rounding, exceptions, JSON field names, and final results.

## Requirements

- JDK 21: `C:\Program Files\Java\jdk-21.0.11`
- Maven: `C:\Users\user\Desktop\apache-maven-3.9.16`

The system `java` command currently points to Java 8, so use the explicit JDK 21 path or set `JAVA_HOME`.

## Run

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
& 'C:\Users\user\Desktop\apache-maven-3.9.16\bin\mvn.cmd' test
```

Run one calculation:

```powershell
& 'C:\Program Files\Java\jdk-21.0.11\bin\java.exe' -cp target\classes com.farmbroker.profit.ProfitCalculatorApplication --space-id 2 --crop-code BASIL
```

Result JSON is written to:

`C:\Users\user\Desktop\해커톤 프로젝트\Profit_Calcaulator_java\output\result.json`

## Data

CSV files are copied from the Python reference `data/` directory into:

`src/main/resources/data/`

They are read as UTF-8 with optional BOM removal, matching Python `utf-8-sig`.

## Tests

JUnit tests cover CSV schema/encoding, required identifiers, current CSV combination execution, error conditions, and nine golden master cases:

`3 spaces x LETTUCE/BASIL/SPROUT_GINSENG`

Golden masters must be regenerated only when the Python reference implementation or reference CSV data intentionally changes.

## Rounding

Python uses `float` calculations and normalizes JSON numbers with `round(value, 6)`, converting integral floats to integers. Java mirrors that behavior with `double` calculations and six-decimal JSON normalization.

## Supported Units

`kg`, `ea`, `root`

Production and sales units must match exactly.

## Known Differences

No calculation or JSON value differences are known after the current golden master test run. Test comparison ignores only CRLF/LF line-ending differences.

