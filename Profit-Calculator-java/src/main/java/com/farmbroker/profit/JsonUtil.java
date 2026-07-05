package com.farmbroker.profit;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class JsonUtil {
    private JsonUtil() {
    }

    static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder builder, Object value, int indent) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            writeString(builder, string);
        } else if (value instanceof Boolean bool) {
            builder.append(bool ? "true" : "false");
        } else if (value instanceof Number number) {
            writeNumber(builder, number);
        } else if (value instanceof Map<?, ?> map) {
            writeMap(builder, (Map<String, Object>) map, indent);
        } else if (value instanceof List<?> list) {
            writeList(builder, list, indent);
        } else {
            writeString(builder, value.toString());
        }
    }

    private static void writeMap(StringBuilder builder, Map<String, Object> map, int indent) {
        builder.append('{');
        if (!map.isEmpty()) {
            builder.append('\n');
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                spaces(builder, indent + 2);
                writeString(builder, entry.getKey());
                builder.append(": ");
                writeValue(builder, entry.getValue(), indent + 2);
                if (iterator.hasNext()) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            spaces(builder, indent);
        }
        builder.append('}');
    }

    private static void writeList(StringBuilder builder, List<?> list, int indent) {
        builder.append('[');
        if (!list.isEmpty()) {
            builder.append('\n');
            Iterator<?> iterator = list.iterator();
            while (iterator.hasNext()) {
                spaces(builder, indent + 2);
                writeValue(builder, iterator.next(), indent + 2);
                if (iterator.hasNext()) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            spaces(builder, indent);
        }
        builder.append(']');
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static void writeNumber(StringBuilder builder, Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            builder.append(number.longValue());
        } else {
            BigDecimal decimal = BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
            builder.append(decimal.toPlainString());
        }
    }

    private static void spaces(StringBuilder builder, int count) {
        builder.append(" ".repeat(count));
    }
}

