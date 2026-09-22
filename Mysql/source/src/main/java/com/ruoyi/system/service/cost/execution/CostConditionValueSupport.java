package com.ruoyi.system.service.cost.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 规则条件的类型和比较语义。
 *
 * <p>规则编辑器、保存校验、预演和正式运行必须共用同一套口径，
 * 否则页面看似允许的日期/字典条件会在运行链中退化成字符串或数值比较。</p>
 */
public final class CostConditionValueSupport {
    private static final Set<String> NUMERIC_TYPES = setOf(
            "NUMBER", "NUMERIC", "DECIMAL", "FLOAT", "INTEGER", "INT", "INT32", "INT64",
            "LONG", "BIGINT", "SMALLINT", "TINYINT", "DOUBLE", "REAL", "BIGDECIMAL");
    private static final Set<String> BOOLEAN_TYPES = setOf("BOOLEAN", "BOOL");
    private static final Set<String> TEXT_TYPES = setOf("STRING", "TEXT", "VARCHAR", "CHAR", "CODE", "ENUM");
    private static final Set<String> DATE_TYPES = setOf(
            "DATE", "DATE_RANGE", "LOCALDATE", "LOCALDATE_RANGE", "YEAR_MONTH", "MONTH",
            "DATETIME", "DATETIME_RANGE", "DATE_TIME", "DATE_TIME_RANGE", "LOCALDATETIME",
            "LOCALDATETIME_RANGE", "TIMESTAMP", "ZONEDDATETIME", "INSTANT", "TIME",
            "TIME_OF_DAY", "TIME_OF_DAY_RANGE", "TIME_RANGE", "LOCALTIME", "LOCALTIME_RANGE");
    private static final Set<String> TIME_ONLY_TYPES = setOf(
            "TIME", "TIME_OF_DAY", "TIME_OF_DAY_RANGE", "TIME_RANGE", "LOCALTIME", "LOCALTIME_RANGE");
    private static final Set<String> OPTION_SOURCES = setOf("PLATFORM_DICT", "BUSINESS_DICT", "BUSINESS_MASTER");

    private CostConditionValueSupport() {
    }

    public enum ValueKind {
        OPTION,
        BOOLEAN,
        NUMBER,
        TEXT,
        TEMPORAL,
        JSON,
        UNKNOWN
    }

    public static ValueKind classify(String variableType, String sourceType, String optionSourceType,
                                     String dictType, String dataType) {
        String variable = normalized(variableType);
        String source = normalized(sourceType);
        String optionSource = normalized(optionSourceType);
        String type = normalized(effectiveDataType(dataType, variableType));
        if ("BUSINESS_MASTER".equals(optionSource) || "BUSINESS_MASTER".equals(source) || "MASTER".equals(source)) {
            return ValueKind.OPTION;
        }
        if (isTemporalDataType(type)) {
            return ValueKind.TEMPORAL;
        }
        if (OPTION_SOURCES.contains(optionSource)
                || isNotBlank(dictType)
                || "DICT".equals(source)
                || "DICT".equals(variable)
                || "DICTIONARY".equals(variable)
                || "ENUM".equals(variable)) {
            return ValueKind.OPTION;
        }
        if (NUMERIC_TYPES.contains(type)) {
            return ValueKind.NUMBER;
        }
        if (BOOLEAN_TYPES.contains(type)) {
            return ValueKind.BOOLEAN;
        }
        if ("JSON".equals(type)) {
            return ValueKind.JSON;
        }
        if (TEXT_TYPES.contains(type) || TEXT_TYPES.contains(variable)) {
            return ValueKind.TEXT;
        }
        return ValueKind.UNKNOWN;
    }

    public static boolean isTemporalDataType(String dataType) {
        String type = normalized(dataType);
        return DATE_TYPES.contains(type);
    }

    public static boolean isNumericDataType(String dataType) {
        return NUMERIC_TYPES.contains(normalized(dataType));
    }

    public static boolean isOptionVariable(String variableType, String sourceType, String optionSourceType,
                                           String dictType, String dataType) {
        return classify(variableType, sourceType, optionSourceType, dictType, dataType) == ValueKind.OPTION;
    }

    public static boolean isOperatorAllowed(String operatorCode, ValueKind kind) {
        String operator = normalized(operatorCode);
        if ("EXPR".equals(operator)) {
            return true;
        }
        if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) {
            return true;
        }
        switch (kind) {
            case OPTION:
                return Arrays.asList("EQ", "NE", "IN", "NOT_IN").contains(operator);
            case BOOLEAN:
                return Arrays.asList("EQ", "NE").contains(operator);
            case NUMBER:
                return Arrays.asList("EQ", "NE", "GT", "GE", "LT", "LE", "BETWEEN", "IN", "NOT_IN").contains(operator);
            case TEMPORAL:
                return Arrays.asList("EQ", "NE", "GT", "GE", "LT", "LE", "BETWEEN").contains(operator);
            case TEXT:
                return Arrays.asList("EQ", "NE", "IN", "NOT_IN").contains(operator);
            case JSON:
                return false;
            default:
                return Arrays.asList("EQ", "NE").contains(operator);
        }
    }

    public static String allowedOperators(ValueKind kind) {
        switch (kind) {
            case OPTION:
                return "EQ,NE,IN,NOT_IN,IS_NULL,IS_NOT_NULL";
            case BOOLEAN:
                return "EQ,NE,IS_NULL,IS_NOT_NULL";
            case NUMBER:
                return "EQ,NE,GT,GE,LT,LE,BETWEEN,IN,NOT_IN,IS_NULL,IS_NOT_NULL";
            case TEMPORAL:
                return "EQ,NE,GT,GE,LT,LE,BETWEEN,IS_NULL,IS_NOT_NULL";
            case TEXT:
                return "EQ,NE,IN,NOT_IN,IS_NULL,IS_NOT_NULL";
            case JSON:
                return "IS_NULL,IS_NOT_NULL,EXPR";
            default:
                return "EQ,NE,IS_NULL,IS_NOT_NULL";
        }
    }

    public static boolean requiresCompareValue(String operatorCode) {
        String operator = normalized(operatorCode);
        return !"IS_NULL".equals(operator) && !"IS_NOT_NULL".equals(operator);
    }

    public static boolean isValidCompareValue(String compareValue, String operatorCode, String dataType,
                                              String variableType, String sourceType, String optionSourceType,
                                              String dictType) {
        if (!requiresCompareValue(operatorCode)) {
            return true;
        }
        List<String> values = splitComparisonValues(compareValue);
        if (values.isEmpty()) {
            return false;
        }
        String effectiveType = effectiveDataType(dataType, variableType);
        ValueKind kind = classify(variableType, sourceType, optionSourceType, dictType, effectiveType);
        String operator = normalized(operatorCode);
        if ("EXPR".equals(operator)) {
            return isNotBlank(compareValue);
        }
        if (!Arrays.asList("IN", "NOT_IN", "BETWEEN").contains(operator) && values.size() != 1) {
            return false;
        }
        if ("BETWEEN".equals(operator) && values.size() != 2) {
            return false;
        }
        if (kind == ValueKind.NUMBER) {
            for (String value : values) {
                if (!isValidDecimal(value)) {
                    return false;
                }
            }
            if ("BETWEEN".equals(operator)
                    && toDecimal(values.get(0)).compareTo(toDecimal(values.get(1))) > 0) {
                return false;
            }
        } else if (kind == ValueKind.BOOLEAN) {
            for (String value : values) {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)
                        && !"1".equals(value) && !"0".equals(value)) {
                    return false;
                }
            }
        } else if (kind == ValueKind.TEMPORAL) {
            for (String value : values) {
                if (parseTemporal(value, effectiveType) == null) {
                    return false;
                }
            }
            if ("BETWEEN".equals(operator) && !isTimeOnlyDataType(dataType)) {
                TemporalPoint start = parseTemporalPoint(values.get(0), effectiveType);
                TemporalPoint end = parseTemporalPoint(values.get(1), effectiveType);
                if (start == null || end == null || start.compareTo(end) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean matches(Object leftValue, String compareValue, String operatorCode, String dataType,
                                  String variableType, String sourceType, String optionSourceType, String dictType) {
        String operator = normalized(operatorCode);
        if ("IS_NULL".equals(operator)) {
            return isBlank(leftValue);
        }
        if ("IS_NOT_NULL".equals(operator)) {
            return !isBlank(leftValue);
        }
        if (leftValue == null || isBlank(leftValue)) {
            return false;
        }
        String effectiveType = effectiveDataType(dataType, variableType);
        ValueKind kind = classify(variableType, sourceType, optionSourceType, dictType, effectiveType);
        List<String> values = splitComparisonValues(compareValue);
        if (values.isEmpty()) {
            return false;
        }
        if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
            boolean contains = false;
            for (String value : values) {
                if (compareSingle(leftValue, value, "EQ", kind, effectiveType)) {
                    contains = true;
                    break;
                }
            }
            return "IN".equals(operator) ? contains : !contains;
        }
        if ("BETWEEN".equals(operator)) {
            if (values.size() < 2) {
                return false;
            }
            if (kind == ValueKind.NUMBER) {
                BigDecimal left = toDecimal(leftValue);
                BigDecimal start = toDecimal(values.get(0));
                BigDecimal end = toDecimal(values.get(1));
                return left != null && start != null && end != null
                        && left.compareTo(start) >= 0 && left.compareTo(end) <= 0;
            }
            if (kind == ValueKind.TEMPORAL) {
                return temporalBetween(leftValue, values.get(0), values.get(1), effectiveType);
            }
            return false;
        }
        return compareSingle(leftValue, compareValue, operator, kind, effectiveType);
    }

    public static List<String> splitValues(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : value.replace('，', ',').split(",")) {
            String normalized = item == null ? "" : item.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Arrays.asList(result.toArray(new String[0]));
    }

    /**
     * 保留区间边界重复值；例如 22:00:00,22:00:00 表示只命中该时刻。
     */
    private static List<String> splitComparisonValues(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (String item : value.replace('，', ',').split(",")) {
            String normalized = item == null ? "" : item.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean compareSingle(Object leftValue, String rightValue, String operator,
                                         ValueKind kind, String dataType) {
        if (kind == ValueKind.NUMBER) {
            BigDecimal left = toDecimal(leftValue);
            BigDecimal right = toDecimal(rightValue);
            if (left == null || right == null) {
                return false;
            }
            return compareNumbers(left, right, operator);
        }
        if (kind == ValueKind.BOOLEAN) {
            Boolean left = toBoolean(leftValue);
            Boolean right = toBoolean(rightValue);
            if (left == null || right == null) {
                return false;
            }
            return "EQ".equals(operator) ? left.equals(right) : "NE".equals(operator) && !left.equals(right);
        }
        if (kind == ValueKind.TEMPORAL) {
            try {
                TemporalValue left = parseTemporal(String.valueOf(leftValue), dataType);
                TemporalValue right = parseTemporal(rightValue, dataType);
                if (left == null || right == null || left.end != null || right.end != null) {
                    return "EQ".equals(operator) && left != null && right != null
                            && left.sameAs(right);
                }
                return compareOrdered(left.compareStart(right), operator);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        int compare = String.valueOf(leftValue).trim().compareTo(String.valueOf(rightValue).trim());
        if ("EQ".equals(operator)) {
            return compare == 0;
        }
        if ("NE".equals(operator)) {
            return compare != 0;
        }
        return false;
    }

    private static boolean temporalBetween(Object leftValue, String startValue, String endValue, String dataType) {
        TemporalValue left = parseTemporal(String.valueOf(leftValue), dataType);
        TemporalValue start = parseTemporal(startValue, dataType);
        TemporalValue end = parseTemporal(endValue, dataType);
        if (left == null || start == null || end == null || start.end != null || end.end != null) {
            return false;
        }
        if (left.end == null) {
            if (left.mode == TemporalMode.TIME) {
                return containsTime(left.start, start.start, end.start);
            }
            return left.compareStart(start) >= 0 && left.compareStart(end) <= 0;
        }
        if (left.mode != TemporalMode.TIME) {
            return left.compareStart(start) >= 0 && left.compareEnd(end) <= 0;
        }
        return containsTime(left.start, start.start, end.start)
                && containsTime(left.end, start.start, end.start);
    }

    private static boolean containsTime(TemporalPoint value, TemporalPoint start, TemporalPoint end) {
        int valueSeconds = value.time.toSecondOfDay();
        int startSeconds = start.time.toSecondOfDay();
        int endSeconds = end.time.toSecondOfDay();
        if (startSeconds <= endSeconds) {
            return valueSeconds >= startSeconds && valueSeconds <= endSeconds;
        }
        return valueSeconds >= startSeconds || valueSeconds <= endSeconds;
    }

    private static boolean compareNumbers(BigDecimal left, BigDecimal right, String operator) {
        int compare = left.compareTo(right);
        return compareOrdered(compare, operator);
    }

    private static boolean compareOrdered(int compare, String operator) {
        switch (operator) {
            case "EQ":
                return compare == 0;
            case "NE":
                return compare != 0;
            case "GT":
                return compare > 0;
            case "GE":
                return compare >= 0;
            case "LT":
                return compare < 0;
            case "LE":
                return compare <= 0;
            default:
                return false;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isValidDecimal(String value) {
        return toDecimal(value) != null;
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return false;
        }
        return null;
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
    }

    private static TemporalValue parseTemporal(String value, String dataType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String[] parts = value.trim().split(",", -1);
        TemporalPoint start = parseTemporalPoint(parts[0].trim(), dataType);
        if (start == null) {
            return null;
        }
        TemporalPoint end = parts.length > 1 && !parts[1].trim().isEmpty()
                ? parseTemporalPoint(parts[1].trim(), dataType) : null;
        return parts.length > 1 && end == null ? null : new TemporalValue(start, end);
    }

    private static TemporalPoint parseTemporalPoint(String value, String dataType) {
        String type = normalized(dataType);
        try {
            if (isTimeOnlyDataType(type)) {
                return TemporalPoint.time(parseLocalTime(value));
            }
            if ("INSTANT".equals(type) || "ZONEDDATETIME".equals(type)) {
                return TemporalPoint.instant(parseInstant(value));
            }
            if ("YEAR_MONTH".equals(type) || "MONTH".equals(type)) {
                return TemporalPoint.yearMonth(YearMonth.parse(value));
            }
            if (type.contains("DATE") && !type.contains("TIME") && !type.contains("DATETIME")) {
                return TemporalPoint.date(LocalDate.parse(value));
            }
            return TemporalPoint.dateTime(parseLocalDateTime(value));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static boolean isTimeOnlyDataType(String dataType) {
        return TIME_ONLY_TYPES.contains(normalized(dataType));
    }

    private static LocalTime parseLocalTime(String value) {
        try {
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException ex) {
            return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT));
        }
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        String normalized = value.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT));
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignored) {
                try {
                    return ZonedDateTime.parse(value).toInstant();
                } catch (DateTimeParseException ignoredAgain) {
                    return LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            .toInstant(ZoneOffset.UTC);
                }
            }
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String effectiveDataType(String dataType, String variableType) {
        return isNotBlank(dataType) ? dataType : variableType;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private enum TemporalMode {
        DATE,
        DATETIME,
        TIME,
        INSTANT,
        YEAR_MONTH
    }

    private static final class TemporalPoint {
        private final TemporalMode mode;
        private final LocalDate date;
        private final LocalDateTime dateTime;
        private final LocalTime time;
        private final Instant instant;
        private final YearMonth yearMonth;

        private TemporalPoint(TemporalMode mode, LocalDate date, LocalDateTime dateTime,
                              LocalTime time, Instant instant, YearMonth yearMonth) {
            this.mode = mode;
            this.date = date;
            this.dateTime = dateTime;
            this.time = time;
            this.instant = instant;
            this.yearMonth = yearMonth;
        }

        private static TemporalPoint date(LocalDate value) {
            return new TemporalPoint(TemporalMode.DATE, value, null, null, null, null);
        }

        private static TemporalPoint dateTime(LocalDateTime value) {
            return new TemporalPoint(TemporalMode.DATETIME, null, value, null, null, null);
        }

        private static TemporalPoint time(LocalTime value) {
            return new TemporalPoint(TemporalMode.TIME, null, null, value, null, null);
        }

        private static TemporalPoint instant(Instant value) {
            return new TemporalPoint(TemporalMode.INSTANT, null, null, null, value, null);
        }

        private static TemporalPoint yearMonth(YearMonth value) {
            return new TemporalPoint(TemporalMode.YEAR_MONTH, null, null, null, null, value);
        }

        private int compareTo(TemporalPoint other) {
            if (other == null || mode != other.mode) {
                throw new IllegalArgumentException("temporal values use different types");
            }
            switch (mode) {
                case DATE:
                    return date.compareTo(other.date);
                case DATETIME:
                    return dateTime.compareTo(other.dateTime);
                case TIME:
                    return time.compareTo(other.time);
                case INSTANT:
                    return instant.compareTo(other.instant);
                case YEAR_MONTH:
                    return yearMonth.compareTo(other.yearMonth);
                default:
                    return 0;
            }
        }
    }

    private static final class TemporalValue {
        private final TemporalPoint start;
        private final TemporalPoint end;
        private final TemporalMode mode;

        private TemporalValue(TemporalPoint start, TemporalPoint end) {
            this.start = start;
            this.end = end;
            this.mode = start.mode;
        }

        private int compareStart(TemporalValue other) {
            return start.compareTo(other.start);
        }

        private int compareEnd(TemporalValue other) {
            return (end == null ? start : end).compareTo(other.end == null ? other.start : other.end);
        }

        private boolean sameAs(TemporalValue other) {
            if (other == null || mode != other.mode) {
                return false;
            }
            return start.compareTo(other.start) == 0
                    && ((end == null && other.end == null)
                    || (end != null && other.end != null && end.compareTo(other.end) == 0));
        }
    }
}
