package com.mauro0805.forceblend.config;

public enum RunMode {
    ONCE("once", "Once per world (default)"),
    EVERY_OPEN("every-open", "Every opening");

    private final String value;
    private final String label;

    RunMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return label;
    }
}
