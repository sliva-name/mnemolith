package com.mnemolith.pressure;

public enum PressureBand {
    CALM("calm"),
    SATURATED("saturated"),
    OVERLOADED("overloaded"),
    FRACTURE("fracture");

    private final String name;

    PressureBand(String name) {
        this.name = name;
    }

    public String translationKey() {
        return "mnemolith.band." + this.name;
    }

    public static PressureBand byOrdinal(int ordinal) {
        PressureBand[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return CALM;
        }
        return values[ordinal];
    }
}
