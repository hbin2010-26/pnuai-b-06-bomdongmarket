# Parity Rules

## Source of Truth

Python under `C:\Users\user\Desktop\해커톤 프로젝트\Profit_Calculator` is the only reference implementation.

## Golden Master

The generated fixtures cover:

- spaces `1`, `2`, `3`
- crops `LETTUCE`, `BASIL`, `SPROUT_GINSENG`

Expected JSON files are written from Python `build_result` without manual numeric edits.

## Comparison

- Strings, booleans, nulls, units, and field names must match.
- Currency values must match Python normalized JSON exactly.
- Physical values are normalized to Python's six-decimal output.
- Test comparison allows only line-ending differences.

## Known Risk Points

- Python `float` rounding is the parity target, not mathematically ideal decimal arithmetic.
- Java must be run with JDK 21, not the Java 8 executable currently on PATH.
- CSV row order and keys should not be modified without regenerating golden masters from a changed Python reference.

