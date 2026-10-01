package com.pavelvoronin.pz3dLoader;

/** Read the native version only after the game has begun initialization. */
public final class GameCompatibility {
    private static Boolean compatible;
    static boolean supports(int major,int minor) { return major>42 || major==42 && minor>=21; }

    public static synchronized boolean check() {
        if (compatible != null) return compatible;
        compatible = false; // Fail closed if the native version API is unavailable.
        try {
            Class<?> core = Class.forName("zombie.core.Core");
            Object instance = core.getMethod("getInstance").invoke(null);
            Object version = core.getMethod("getGameVersion").invoke(instance);
            int major = (int)version.getClass().getMethod("getMajor").invoke(version);
            int minor = (int)version.getClass().getMethod("getMinor").invoke(version);
            compatible = supports(major,minor);
            if (!compatible) {
                Agent.status = "UNSUPPORTED";
                System.err.println("[pz3d Loader] Requires Project Zomboid 42.21 or newer; detected " + version + "; PZ3D will not be loaded by Pz3dLoader");
            }
        } catch (Exception error) { Agent.failed(new IllegalStateException("Cannot determine the native game version", error)); }
        return compatible;
    }
}
