package com.pavelvoronin.pz3dLoader;

import java.lang.instrument.Instrumentation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Specialized advice-only path based on ZombieBuddy's grouping and argument matching. */
public final class PatchEngine {
    private PatchEngine() {}
    public static void install(Instrumentation instrumentation, Class<?> declarations) throws Exception {
        Map<String,List<Class<?>>> byTarget = new TreeMap<>();
        Class<?>[] classes = declarations.getDeclaredClasses();
        Arrays.sort(classes,Comparator.comparing(Class::getName));
        for (Class<?> type : classes) {
            Patch patch = type.getAnnotation(Patch.class);
            if (patch == null) continue;
            if (!patch.isAdvice()) throw new IllegalStateException("Only PZ3D advice patches are supported: " + type.getName());
            if (!patch.className().startsWith("zombie.") && !Set.of("fmod.fmod.SoundListener", "se.krka.kahlua.vm.KahluaThread", "org.lwjglx.opengl.Display").contains(patch.className())) throw new IllegalStateException("Unexpected patch target: " + patch.className());
            byTarget.computeIfAbsent(patch.className(), key -> new ArrayList<>()).add(type);
        }
        if (byTarget.isEmpty()) throw new IllegalStateException("No PZ3D patches found");
        // Resolve every target before installing a single transformer, avoiding recursive-load gaps.
        for (String target : byTarget.keySet()) Class.forName(target,false,declarations.getClassLoader());
        Set<String> transformed = ConcurrentHashMap.newKeySet();
        Map<String,Throwable> errors = new ConcurrentHashMap<>();
        AgentBuilder builder = new AgentBuilder.Default().disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new AgentBuilder.Listener.Adapter() {
                @Override public void onTransformation(TypeDescription type,ClassLoader loader,JavaModule module,boolean loaded,DynamicType result) { transformed.add(type.getName()); }
                @Override public void onError(String name,ClassLoader loader,JavaModule module,boolean loaded,Throwable error) { errors.put(name,error); }
            });
        for (var entry : byTarget.entrySet()) {
            builder = builder.type(named(entry.getKey())).transform((output,type,loader,module,domain) -> {
                for (Class<?> advice : entry.getValue()) {
                    Patch patch = advice.getAnnotation(Patch.class);
                    var matcher = AdviceMatcher.buildAdviceMethodMatcher(patch.methodName(),patch.strictMatch(),advice,null);
                    if (type.getDeclaredMethods().filter(matcher).isEmpty()) throw new IllegalStateException("No matching method for " + advice.getName());
                    output = output.visit(Advice.to(advice).on(matcher));
                }
                return output;
            });
        }
        var transformer = builder.installOn(instrumentation);
        if (!errors.isEmpty() || !transformed.containsAll(byTarget.keySet())) {
            transformer.reset(instrumentation,AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
            throw new IllegalStateException("PZ3D patch installation failed: transformed=" + transformed.size() + "/" + byTarget.size() + ", errors=" + errors);
        }
        System.out.println("[pz3d Loader] Installed " + Arrays.stream(classes).filter(c -> c.isAnnotationPresent(Patch.class)).count() + " patches in " + transformed.size() + " classes");
    }
}
