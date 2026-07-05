package com.farmbroker.profit;

import java.util.LinkedHashMap;
import java.util.Map;

final class CsvRow {
    private final Map<String, String> values;

    CsvRow(Map<String, String> values) {
        this.values = new LinkedHashMap<>(values);
    }

    String get(String field) {
        return values.get(field);
    }

    Map<String, String> asMap() {
        return new LinkedHashMap<>(values);
    }

    double doubleValue(String field) {
        try {
            return Double.parseDouble(get(field));
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException(field + " must be numeric.", exc);
        }
    }

    int intValue(String field) {
        try {
            return Integer.parseInt(get(field));
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException(field + " must be an integer.", exc);
        }
    }

    @Override
    public String toString() {
        return values.toString();
    }
}

