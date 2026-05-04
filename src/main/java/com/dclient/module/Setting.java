package com.dclient.module;

public class Setting<T> {
    public final String name;
    private T value;
    private final T defaultValue;
    // Optional bounds for numeric settings (null = unbounded)
    public final T min;
    public final T max;
    // Optional options list for String settings
    public final String[] options;

    public Setting(String name, T defaultValue) {
        this(name, defaultValue, null, null, null);
    }

    public Setting(String name, T defaultValue, T min, T max) {
        this(name, defaultValue, min, max, null);
    }

    public Setting(String name, T defaultValue, String[] options) {
        this(name, defaultValue, null, null, options);
    }

    public Setting(String name, T defaultValue, T min, T max, String[] options) {
        this.name = name;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.options = options;
    }

    public T getValue() { return value; }

    @SuppressWarnings("unchecked")
    public void setValue(T value) {
        if (min != null && max != null) {
            if (value instanceof Float f) {
                float lo = (Float) min, hi = (Float) max;
                value = (T)(Float) Math.max(lo, Math.min(hi, f));
            } else if (value instanceof Integer i) {
                int lo = (Integer) min, hi = (Integer) max;
                value = (T)(Integer) Math.max(lo, Math.min(hi, i));
            }
        }
        this.value = value;
    }

    public T getDefaultValue() { return defaultValue; }
    public boolean hasBounds() { return min != null && max != null; }

    @Override
    public String toString() { return value.toString(); }
}
