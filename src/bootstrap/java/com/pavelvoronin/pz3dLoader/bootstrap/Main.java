package com.pavelvoronin.pz3dLoader.bootstrap;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.jar.*;

/** Patch-free entry point shared by both loaders. The core is never on its scan path. */
public final class Main {
    public interface Starter { void start(Path core) throws Exception; }
    private static Starter starter;
    private static Path bundle;
    private static String state = "NEW", owner = "";
    private static final String AUTHOR = "76561198008387517";
    private static final String KEY = "3e79bf4e87803479617b529ef2603f07ce117f11b47546edadac080669dc52ba";

    public static synchronized void register(Path path, Starter callback) throws Exception {
        bind(path);
        if (starter != null) throw new IllegalStateException("Pz3dLoader already registered");
        starter = callback;
        // A premain preload may have requested us before PZ initialized its filesystem/Lua.
        // Registration alone must not run the core; the native enabled-mod phase requests it.
    }

    public static void main(String[] args) throws Exception {
        Path path;
        synchronized (Main.class) { path = bundle; }
        Path discovered = locateBundle();
        if (discovered != null) path = discovered;
        if (path == null) throw new IllegalStateException("PZ3D bootstrap payload is missing");
        request(path);
    }

    private static Path locateBundle() throws Exception {
        // The JVM may already resolve this shared class from pz3dLoader.jar before its premain.
        // Locate the payload resource, not the class's protection domain in that case.
        Set<Path> candidates = new HashSet<>();
        var resources = Main.class.getClassLoader().getResources("engine/core.jar");
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            if (!resource.getProtocol().equals("jar")) continue;
            var connection = (java.net.JarURLConnection) resource.openConnection();
            connection.setUseCaches(false);
            Path path = Path.of(connection.getJarFileURL().toURI()).toRealPath();
            try (JarFile jar = new JarFile(path.toFile())) {
                if (jar.getManifest() != null && "1".equals(jar.getManifest().getMainAttributes().getValue("Pz3dLoader-Bootstrap"))) candidates.add(path);
            }
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() != 1) throw new IllegalStateException("Conflicting PZ3D bootstrap payloads");
        return candidates.iterator().next();
    }

    private static void bind(Path path) throws Exception {
        path = path.toRealPath();
        if (bundle != null && !bundle.equals(path)) throw new IllegalStateException("Conflicting PZ3D bootstrap packages");
        bundle = path;
    }

    public static synchronized void request(Path path) throws Exception {
        bind(path);
        if (state.equals("READY") || state.equals("STARTING")) return;
        if (state.equals("FAILED")) throw new IllegalStateException("Previous PZ3D startup failed; restart required");
        if (owner.isEmpty()) owner = plannedPz3dLoader() ? "Pz3dLoader" : "ZombieBuddy";
        if (owner.equals("Pz3dLoader") && starter == null) { state = "WAITING"; return; }
        state = "STARTING";
        try {
            Path core = extractVerifiedCore(bundle);
            if (owner.equals("Pz3dLoader")) starter.start(core);
            else loadWithZombieBuddy(core);
            state = "READY";
            System.out.println("[pz3d Loader bootstrap] PZ3D owner=" + owner);
        } catch (Exception | Error error) { state = "FAILED"; throw error; }
    }

    public static synchronized String state() { return state; }
    public static synchronized String owner() { return owner; }

    private static boolean plannedPz3dLoader() throws Exception {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.equals("-agentlib:pz3dLoader") || argument.startsWith("-agentlib:pz3dLoader=")) return true;
            if (argument.startsWith("-agentpath:") && Path.of(argument.substring(11).split("=",2)[0]).getFileName().toString().equalsIgnoreCase("pz3dLoader.dll")) return true;
            if (!argument.startsWith("-javaagent:")) continue;
            try (JarFile jar = new JarFile(argument.substring(11).split("=", 2)[0])) {
                var manifest = jar.getManifest();
                if (manifest != null && "com.pavelvoronin.pz3dLoader.Agent".equals(manifest.getMainAttributes().getValue("Premain-Class"))) return true;
            }
        }
        return false;
    }

    public static Path extractVerifiedCore(Path path) throws Exception {
        byte[] bytes, signature;
        try (JarFile jar = new JarFile(path.toFile())) {
            bytes = read(jar, "engine/core.jar", 128 * 1024 * 1024);
            signature = read(jar, "engine/core.jar.zbs", 512);
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String prefix = "ZBS\nSteamID64:" + AUTHOR + "\nSignature:";
        String text = new String(signature, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!text.startsWith(prefix)) throw new SecurityException("Invalid core signature header");
        String hex = text.substring(prefix.length()).stripTrailing();
        if (!hex.matches("[0-9a-fA-F]{128}")) throw new SecurityException("Invalid core signature");
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(HexFormat.of().parseHex("302a300506032b6570032100" + KEY))));
        verifier.update(("ZBS:" + AUTHOR + ":" + hash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!verifier.verify(HexFormat.of().parseHex(hex))) throw new SecurityException("PZ3D core signature rejected");
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "pz3dLoader-core", hash);
        Files.createDirectories(root);
        Path core = root.resolve("core.jar");
        store(core, bytes); store(root.resolve("core.jar.zbs"), signature);
        return core;
    }

    private static byte[] read(JarFile jar, String name, int limit) throws Exception {
        var entry = jar.getJarEntry(name);
        if (entry == null || entry.getSize() > limit) throw new SecurityException("Missing or oversized " + name);
        try (var stream = jar.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new SecurityException("Oversized " + name);
            return bytes;
        }
    }

    private static void store(Path path, byte[] bytes) throws Exception {
        if (!Files.exists(path)) {
            Path pending = Files.createTempFile(path.getParent(), "pending-", ".tmp");
            try {
                Files.write(pending, bytes);
                try { Files.move(pending, path); } catch (FileAlreadyExistsException concurrent) { }
            } finally { Files.deleteIfExists(pending); }
        }
        if (!MessageDigest.isEqual(bytes, Files.readAllBytes(path))) throw new SecurityException("Corrupted core cache");
    }

    private static void loadWithZombieBuddy(Path core) throws Exception {
        // Compatibility adapter tested against unmodified ZombieBuddy 2.3.2.
        Class<?> loader = Class.forName("me.zed_0xff.zombie_buddy.Loader");
        Class<?> phase = Class.forName("me.zed_0xff.zombie_buddy.Loader$Phase");
        Object main = Arrays.stream(phase.getEnumConstants()).filter(v -> v.toString().equals("MAIN")).findFirst().orElseThrow();
        var method = loader.getDeclaredMethod("loadJar", Path.class, String.class, String.class, phase);
        method.setAccessible(true);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(core)));
        if (!Boolean.TRUE.equals(method.invoke(null, core, "com.pavelvoronin.pz3d", hash, main))) throw new IllegalStateException("ZombieBuddy refused PZ3D core");
    }
}
