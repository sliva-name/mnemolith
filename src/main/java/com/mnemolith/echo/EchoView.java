package com.mnemolith.echo;

/**
 * What the local client is looking at through the lens. Plain statics in common code so
 * {@link com.mnemolith.entity.echo.EchoEntity} can read them on the client without touching client classes.
 * The dedicated server never writes these, so every echo there stays unlit.
 */
public final class EchoView {
    /** Light pink outline for every echo while the thermal view is up. */
    public static final int OUTLINE = 0xFF8FCB;
    /** Brightest step of the targeted pulse. */
    public static final int TARGET = 0xFFF2FA;

    private static volatile boolean thermal;
    private static volatile int targetId = -1;
    private static volatile boolean possessed;

    private EchoView() {}

    public static boolean thermal() {
        return thermal;
    }

    public static int targetId() {
        return targetId;
    }

    public static boolean possessed() {
        return possessed;
    }

    public static void setThermal(boolean value) {
        thermal = value;
        if (!value) {
            targetId = -1;
        }
    }

    public static void setTarget(int id) {
        targetId = id;
    }

    public static void setPossessed(boolean value) {
        possessed = value;
    }

    /** Outline color for one echo. The target pulses between white-pink and the outline pink about twice a second. */
    public static int outlineColor(int entityId) {
        if (entityId != targetId) {
            return OUTLINE;
        }
        double phase = (System.currentTimeMillis() % 600L) / 600.0D * Math.PI * 2.0D;
        float t = (float) (0.5D + 0.5D * Math.sin(phase));
        return lerp(0xFFC6E6, TARGET, t);
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
