package rv.trading.strategy.engine.core.indicator;

import java.util.Map;
import java.util.Objects;
import java.util.Locale;

import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

public final class IndicatorParams {

    private IndicatorParams() {
    }

    public static int intValue(IndicatorCode code, Map<String, Object> params, String key, int defaultValue) {
        Object value = value(code, params, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (Double.isFinite(doubleValue) && doubleValue == Math.rint(doubleValue)
                    && doubleValue >= Integer.MIN_VALUE && doubleValue <= Integer.MAX_VALUE) {
                return (int) doubleValue;
            }
        } else if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException exception) {
                throw invalid(code, key, value);
            }
        }
        throw invalid(code, key, value);
    }

    public static double doubleValue(IndicatorCode code, Map<String, Object> params, String key, double defaultValue) {
        Object value = value(code, params, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            double result = value instanceof Number number ? number.doubleValue()
                    : value instanceof String string ? Double.parseDouble(string.trim()) : Double.NaN;
            if (Double.isFinite(result)) {
                return result;
            }
        } catch (NumberFormatException exception) {
            throw invalid(code, key, value);
        }
        throw invalid(code, key, value);
    }

    public static <E extends Enum<E>> E enumValue(Map<String, Object> params, String key, Class<E> enumType,
            E defaultValue) {
        return enumValue(null, params, key, enumType, defaultValue);
    }

    public static <E extends Enum<E>> E enumValue(IndicatorCode code, Map<String, Object> params, String key,
            Class<E> enumType, E defaultValue) {
        Object value = value(code, params, key);
        if (value == null) {
            return defaultValue;
        }
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        if (!(value instanceof String string) || string.isBlank()) {
            throw invalid(code, key, value);
        }
        try {
            return Enum.valueOf(enumType, string.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(code, key, value);
        }
    }

    public static int positiveInt(IndicatorCode code, Map<String, Object> params, String key, int defaultValue) {
        int result = intValue(code, params, key, defaultValue);
        if (result < 1) {
            throw new IndicatorConfigurationException(code, "Parameter '" + key + "' must be positive");
        }
        return result;
    }

    public static void require(IndicatorCode code, boolean valid, String message) {
        if (!valid) {
            throw new IndicatorConfigurationException(code, message);
        }
    }

    private static Object value(IndicatorCode code, Map<String, Object> params, String key) {
        Objects.requireNonNull(key, "key");
        if (params == null || !params.containsKey(key)) {
            return null;
        }
        Object value = params.get(key);
        if (value == null) {
            throw new IndicatorConfigurationException(code, "Parameter '" + key + "' must not be null");
        }
        return value;
    }

    private static IndicatorConfigurationException invalid(IndicatorCode code, String key, Object value) {
        return new IndicatorConfigurationException(code, "Invalid value for parameter '" + key + "': " + value);
    }
}
