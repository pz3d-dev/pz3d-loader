package com.pavelvoronin.pz3dLoader;

import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;

/** Checks every advice against supplied game classes without starting the game or running PZ3D. */
public final class CompatibilityCheck {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Expected PZ3D JAR; game JAR must be on classpath");
        Path adapted = Files.createTempFile("pz3d-compatibility-", ".jar");
        try {
            Files.write(adapted,Pz3dAdapter.adaptJar(Files.readAllBytes(Path.of(args[0]))));
            try(var loader = new URLClassLoader(new java.net.URL[]{adapted.toUri().toURL()},CompatibilityCheck.class.getClassLoader())) {
                Class<?> declarations = Class.forName("com.pavelvoronin.pz3d.Patches",false,loader);
                Map<String,List<Class<?>>> groups = new TreeMap<>();
                int count = 0;
                for(Class<?> type:declarations.getDeclaredClasses()) {
                    Patch patch = type.getAnnotation(Patch.class);
                    if(patch!=null) { groups.computeIfAbsent(patch.className(),key->new ArrayList<>()).add(type); count++; }
                }
                for(var group:groups.entrySet()) {
                    Class<?> target = Class.forName(group.getKey(),false,loader);
                    TypeDescription description = new TypeDescription.ForLoadedType(target);
                    DynamicType.Builder<?> builder = new ByteBuddy().redefine(target);
                    for(Class<?> advice:group.getValue()) {
                        Patch patch = advice.getAnnotation(Patch.class);
                        var matcher = AdviceMatcher.buildAdviceMethodMatcher(patch.methodName(),patch.strictMatch(),advice,null);
                        if(description.getDeclaredMethods().filter(matcher).isEmpty()) throw new IllegalStateException("No matching methods: " + advice.getName());
                        builder = builder.visit(Advice.to(advice).on(matcher));
                    }
                    try(var output=builder.make()) { if(output.getBytes().length==0) throw new AssertionError(); }
                }
                System.out.println("PASS " + count + " PZ3D advices against " + groups.size() + " game classes; game and PZ3D entrypoints were not invoked");
            }
        } finally { Files.deleteIfExists(adapted); }
    }
}
