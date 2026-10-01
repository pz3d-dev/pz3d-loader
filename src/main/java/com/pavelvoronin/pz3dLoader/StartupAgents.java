package com.pavelvoronin.pz3dLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Inspect every startup agent before touching PZ3D, regardless of premain order. */
public final class StartupAgents {
    private StartupAgents() {}
    public static boolean hasZombieBuddy(List<String> arguments) throws Exception {
        for (String argument : arguments) {
            if (argument.equals("-agentlib:zbNative") || argument.startsWith("-agentlib:zbNative=")) return true;
            if (argument.startsWith("-agentpath:") && Path.of(argument.substring(11).split("=",2)[0]).getFileName().toString().equalsIgnoreCase("zbNative.dll")) return true;
            if (!argument.startsWith("-javaagent:")) continue;
            String value = argument.substring("-javaagent:".length());
            int options = value.indexOf('=');
            String path = options < 0 ? value : value.substring(0, options);
            try (JarFile jar = new JarFile(Path.of(path).toFile())) {
                var manifest = jar.getManifest();
                if (manifest != null && "me.zed_0xff.zombie_buddy.Agent".equals(
                        manifest.getMainAttributes().getValue("Premain-Class"))) return true;
            }
        }
        return false;
    }
}
