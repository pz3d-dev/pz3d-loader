package com.pavelvoronin.pz3dLoader;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.*;

public final class Agent {
    // Name retained by the private bytecode adapter for PZ3D's optional chunk probe.
    public static Instrumentation g_instrumentation;
    private static JarFile loadedJar;
    private static volatile Class<?> main;
    private static boolean initialized, attempted;
    private static Path jar;
    private static boolean bootstrap, buddy, locallyEnabled;
    private static String commonDirectory;
    public static volatile String status = "STARTING";
    public static volatile String modVersion = "";
    public static volatile String failure = "";

    public static synchronized void premain(String arguments, Instrumentation instrumentation) throws Exception {
        if (initialized) return;
        initialized = true;
        buddy = StartupAgents.hasZombieBuddy(ManagementFactory.getRuntimeMXBean().getInputArguments());
        g_instrumentation = instrumentation;
        if (!RuntimeRole.serverCommand()) LoaderUi.install(instrumentation);
        try {
            var hostLaunch = HostServerLauncher.substitution();
            if (arguments != null && !arguments.isBlank()) throw new IllegalArgumentException("Pz3dLoader takes no agent options; enable pz3d in the game");
            new AgentBuilder.Default().disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
                .type(named("zombie.ZomboidFileSystem"))
                .transform((builder,type,loader,module,domain) -> builder.visit(Advice.to(ModListHook.class).on(named("loadMods").and(takesArguments(List.class)))))
                .type(named("zombie.Lua.LuaManager$Exposer"))
                .transform((builder,type,loader,module,domain) -> builder.visit(Advice.to(ExposeHook.class).on(named("exposeAll").and(takesArguments(0)))))
                .type(named("zombie.network.CoopMaster"))
                .transform((builder,type,loader,module,domain) -> builder.visit(hostLaunch))
                .type(named("zombie.network.ServerOptions"))
                .transform((builder,type,loader,module,domain) -> builder.visit(Advice.to(ViewOptionsConstructor.class).on(isConstructor())).visit(Advice.to(ViewOptionsInit.class).on(named("init").and(takesArguments(0)))))
                .installOn(instrumentation);
            System.out.println("[pz3d Loader] Waiting for enabled pz3d mod");
        } catch (Exception error) { failed(error); }
    }

    public static class ModListHook {
        @Advice.OnMethodExit public static void exit(@Advice.This Object filesystem) {
            try { Agent.loadNativeMods((List<String>) filesystem.getClass().getMethod("getModIDs").invoke(filesystem)); } catch (Exception error) { if (!Agent.status.equals("FAILED")) Agent.failed(error); }
        }
    }
    public static class ExposeHook {
        @Advice.OnMethodExit public static void exit() throws Exception { Agent.expose(); }
    }

    public static class ViewOptionsConstructor {
        @Advice.OnMethodExit public static void exit(@Advice.This Object options) throws Exception { ViewOptions.register(options); }
    }
    public static class ViewOptionsInit {
        // PZ reads and rewrites the server INI before enabled Java mods start.
        @Advice.OnMethodEnter public static void enter(@Advice.This Object options) throws Exception { ViewOptions.register(options); }
    }

    public static void loadNativeMods(List<String> mods) throws Exception {
        loadIfEnabled(mods);
    }

    public static void loadIfEnabled(List<String> mods) throws Exception {
        if (!GameCompatibility.check()) return;
        boolean server = RuntimeRole.server(), client = !server && RuntimeRole.client();
        boolean enabled = mods.contains("pz3d") || RuntimeRole.optionalServer(server);
        synchronized (Agent.class) {
            if (attempted || Set.of("FAILED","LEGACY","READY").contains(status)) {
                if (!server && !client) locallyEnabled = mods.contains("pz3d");
                boolean retained = RuntimeRole.retainLocal(server,client,locallyEnabled);
                if (status.equals("READY") && !enabled && !retained) status="RESTART";
                else if (status.equals("RESTART") && (enabled || retained)) status="READY";
                if (main != null && !server) configureClient(main);
                return;
            }
            try {
                Class<?> chooser = Class.forName("zombie.gameStates.ChooseGameInfo");
                Object selected = chooser.getMethod("getModDetails",String.class).invoke(null,"pz3d");
                if (selected == null) { modVersion = ""; status = "MISSING"; return; }
                String detectedVersion = (String)selected.getClass().getMethod("getModVersion").invoke(selected);
                modVersion = detectedVersion != null && detectedVersion.matches("[A-Za-z0-9.+_-]{1,40}") ? detectedVersion : "?";
                SelfUpdater.discover(selected);
                if(!(boolean)selected.getClass().getMethod("isAvailableSelf").invoke(selected)) { status="INCOMPATIBLE"; return; }
                if (!enabled) { status = "DISABLED"; return; }
                attempted = true;
                locallyEnabled = !server && !client;
                selected = chooser.getMethod("getAvailableModDetails",String.class).invoke(null,"pz3d");
                if (selected == null) throw new IllegalStateException("Enabled PZ3D package is unavailable");
                String version = (String)selected.getClass().getMethod("getVersionDir").invoke(selected);
                String common = (String)selected.getClass().getMethod("getCommonDir").invoke(selected);
                commonDirectory = common;
                SelectedMod.Package found = SelectedMod.resolve(version == null ? null : Path.of(version), common == null ? null : Path.of(common));
                jar = found.jar();
                try (JarFile file = new JarFile(jar.toFile())) {
                    bootstrap = file.getManifest() != null && "1".equals(file.getManifest().getMainAttributes().getValue("Pz3dLoader-Bootstrap"));
                }
                if (bootstrap != found.bootstrap()) throw new SecurityException("PZ3D package metadata does not match its manifest");
                if (buddy && !bootstrap) {
                    status = "LEGACY";
                    System.out.println("[pz3d Loader] Startup owner: ZombieBuddy; legacy PZ3D package, no Pz3dLoader core hooks installed");
                    return;
                }
                verify(jar);
                if (bootstrap) com.pavelvoronin.pz3dLoader.bootstrap.Main.register(jar, Agent::startCore);
            } catch (Exception error) { failed(error); throw error; }
        }
        try {
            if (bootstrap) com.pavelvoronin.pz3dLoader.bootstrap.Main.request(jar);
            else startCore(jar);
        } catch (Exception error) { failed(error); throw error; }
    }

    public static void failed(Throwable error) {
        status = "FAILED";
        failure = error.toString();
        System.err.println("[pz3d Loader] PZ3D startup failed: " + error);
        error.printStackTrace(System.err);
    }

    private static byte[] verify(Path source) throws Exception {
        if (Files.size(source) > 128L * 1024 * 1024) throw new SecurityException("PZ3D JAR too large");
        byte[] original = Files.readAllBytes(source);
        String key;
        try (var stream = Agent.class.getResourceAsStream("/pz3d-public-key.hex")) {
            if (stream == null) throw new SecurityException("Missing embedded public key");
            key = new String(stream.readAllBytes(),StandardCharsets.US_ASCII).trim();
        }
        String hash = JarSignature.verify(original,Path.of(source + ".zbs"),JarSignature.publicKey(key));
        System.out.println("[pz3d Loader] Verified official PZ3D sha256=" + hash);
        return original;
    }

    private static void startCore(Path source) throws Exception {
        status = "LOADING";
        for (Class<?> loaded : g_instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals("com.pavelvoronin.pz3d.Main"))
                throw new IllegalStateException("A PZ3D core is already loaded; refusing duplicate initialization");
        }
        byte[] original = verify(source);
        byte[] adapted = Pz3dAdapter.adaptJar(original);
        Path cached = AdaptedCache.store(adapted);
        loadedJar = new JarFile(cached.toFile());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { loadedJar.close(); }
            catch (java.io.IOException error) { System.err.println("[pz3d Loader] JAR close: " + error); }
        },"Pz3dLoader-cleanup"));
        g_instrumentation.appendToSystemClassLoaderSearch(loadedJar);
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        boolean server = RuntimeRole.server();
        Class<?> candidate = Class.forName(RuntimeRole.entry(server),false,loader);
        if (!server) configureClient(candidate);
        candidate.getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        PatchEngine.install(g_instrumentation,Class.forName(RuntimeRole.patches(server),false,loader));
        main = candidate;
        expose();
        status = "READY";
        System.out.println("[pz3d Loader] PZ3D initialized");
    }

    private static void configureClient(Class<?> entry) throws Exception {
        if (commonDirectory == null) return;
        try { entry.getMethod("configureClient",String.class,boolean.class).invoke(null,commonDirectory,locallyEnabled); }
        catch (NoSuchMethodException olderCore) { /* Preserve compatibility with released SP cores. */ }
    }

    /** Uses the game's native Lua exposer, with the same alias as ZombieBuddy. */
    public static synchronized void expose() throws Exception {
        if (RuntimeRole.server()) return;

        Class<?> lua = Class.forName("zombie.Lua.LuaManager");
        Object exposer = lua.getField("exposer").get(null);
        Object env = lua.getField("env").get(null);
        if (exposer == null || env == null) return;
        Class<?> table = Class.forName("se.krka.kahlua.vm.KahluaTable");
        table.getMethod("rawset",Object.class,Object.class).invoke(env,"pz3dLoaderAgent",Boolean.TRUE);
        if (main == null) return;
        exposer.getClass().getMethod("setExposed",Class.class).invoke(exposer,main);
        exposer.getClass().getMethod("exposeLikeJavaRecursively",java.lang.reflect.Type.class,table).invoke(exposer,main,env);
        Object value = table.getMethod("rawget",Object.class).invoke(env,"Main");
        table.getMethod("rawset",Object.class,Object.class).invoke(env,"PZ3D",value);
        table.getMethod("rawset",Object.class,Object.class).invoke(env,"Main",null);
    }
}
