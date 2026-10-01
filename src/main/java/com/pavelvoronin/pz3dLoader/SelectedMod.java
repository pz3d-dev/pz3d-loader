package com.pavelvoronin.pz3dLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resolve Java metadata only inside the version/common folders selected by PZ. */
public final class SelectedMod {
    public record Package(Path jar, boolean bootstrap) {}
    public static Package resolve(Path version, Path common) throws Exception {
        Path[] sources = {version, common, common, version};
        Path[] roots = {version, common, version, common};
        for (int i=0; i<sources.length; i++) {
            if (sources[i] == null || roots[i] == null) continue;
            if (i == 3 && common != null && Files.isRegularFile(common.resolve("mod.info"))) continue;
            Path info = sources[i].resolve("mod.info");
            if (!Files.isRegularFile(info)) continue;
            Map<String,String> fields = new HashMap<>();
            for (String line : Files.readAllLines(info, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals < 0) continue;
                String key = line.substring(0,equals).toLowerCase(Locale.ROOT);
                String value = line.substring(equals+1).trim();
                if (!value.isEmpty()) fields.putIfAbsent(key,value);
            }
            String relative = fields.get("javajarfile"), pkg = fields.get("javapkgname");
            if (relative == null || pkg == null) continue;
            if (!Set.of("com.pavelvoronin.pz3d", "com.pavelvoronin.pz3dLoader.bootstrap").contains(pkg)) throw new SecurityException("Unexpected PZ3D Java package");
            Path root = roots[i].toAbsolutePath().normalize();
            Path jar = root.resolve(relative).normalize();
            if (Path.of(relative).isAbsolute() || !jar.startsWith(root)) throw new SecurityException("PZ3D JAR must be inside its mod folder");
            if (!Files.isRegularFile(jar)) continue;
            jar = jar.toRealPath();
            if (!jar.startsWith(root.toRealPath())) throw new SecurityException("PZ3D JAR escapes its mod folder");
            return new Package(jar, pkg.endsWith(".bootstrap"));
        }
        throw new IllegalStateException("The enabled PZ3D package has no available Java core");
    }
}
