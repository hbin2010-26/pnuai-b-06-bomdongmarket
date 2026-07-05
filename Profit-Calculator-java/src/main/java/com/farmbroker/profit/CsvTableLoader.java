package com.farmbroker.profit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CsvTableLoader {
    private CsvTableLoader() {
    }

    static DataTables load(Path dataDir) throws IOException {
        return new DataTables(
                read(dataDir.resolve("spaces.csv")),
                read(dataDir.resolve("crops.csv")),
                read(dataDir.resolve("sales.csv")),
                read(dataDir.resolve("crop_materials.csv")),
                read(dataDir.resolve("packaging.csv")),
                read(dataDir.resolve("operating_costs.csv")),
                read(dataDir.resolve("profit_sharing.csv")),
                read(dataDir.resolve("environment_standards.csv")),
                read(dataDir.resolve("equipment_standards.csv")),
                read(dataDir.resolve("utility_rates.csv")),
                read(dataDir.resolve("operating_policies.csv")),
                read(dataDir.resolve("seasonal_conditions.csv")),
                read(dataDir.resolve("calendar_profiles.csv"))
        );
    }

    static List<CsvRow> read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<List<String>> records = parse(text);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> headers = records.get(0);
        List<CsvRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.size() == 1 && record.get(0).isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < record.size() ? record.get(j) : "");
            }
            rows.add(new CsvRow(row));
        }
        return rows;
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                record.add(stripCarriageReturn(field.toString()));
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(stripCarriageReturn(field.toString()));
            records.add(record);
        }
        return records;
    }

    private static String stripCarriageReturn(String value) {
        return value.endsWith("\r") ? value.substring(0, value.length() - 1) : value;
    }
}

