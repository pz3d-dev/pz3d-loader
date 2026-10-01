package com.pavelvoronin.pz3dLoader;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Converts only the annotation subset used by PZ3D; never ships ZombieBuddy namespace classes. */
public final class Pz3dAdapter {
    private static final String ZB = "me/zed_0xff/zombie_buddy/";
    private static final String OWN = "com/pavelvoronin/pz3dLoader/";
    private static final String ADVICE = "net/bytebuddy/asm/Advice$";
    private Pz3dAdapter() {}

    public static byte[] adaptClass(byte[] source) {
        ClassReader reader = new ClassReader(source);
        ClassWriter writer = new ClassWriter(0);
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                String originalBuddy = String.join("/", "net", "bytebuddy", "");
                if (name.startsWith(originalBuddy)) {
                    String actualBuddy = net.bytebuddy.asm.Advice.class.getName().replace('.','/').replace("asm/Advice", "");
                    return actualBuddy + name.substring(originalBuddy.length());
                }
                if (!name.startsWith(ZB)) return name;
                String suffix = name.substring(ZB.length());
                if (suffix.startsWith("annotations/")) suffix = suffix.substring("annotations/".length());
                return switch(suffix) {
                    case "Patch" -> OWN + "Patch";
                    case "Exposer" -> OWN + "Exposer";
                    case "Exposer$LuaClass" -> OWN + "Exposer$LuaClass";
                    case "Loader" -> OWN + "Agent";
                    case "Patch$OnEnter" -> ADVICE + "OnMethodEnter";
                    case "Patch$OnExit" -> ADVICE + "OnMethodExit";
                    case "Patch$Argument", "Patch$This", "Patch$Return", "Patch$AllArguments" -> ADVICE + suffix.substring(6);
                    default -> throw new IllegalArgumentException("Unsupported ZombieBuddy API: " + name);
                };
            }
        };
        ClassVisitor convert = new ClassRemapper(writer, remapper) {
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access,name,descriptor,signature,exceptions)) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                        AnnotationVisitor delegate = super.visitAnnotation(descriptor,visible);
                        if (!descriptor.equals("L" + ZB + "Patch$OnEnter;") && !descriptor.equals("L" + ZB + "annotations/Patch$OnEnter;")) return delegate;
                        return new AnnotationVisitor(Opcodes.ASM9, delegate) {
                            @Override public void visit(String key,Object value) {
                                if (key.equals("skipOn")) {
                                    if (Boolean.TRUE.equals(value)) super.visit(key,Type.getType("L" + ADVICE + "OnNonDefaultValue;"));
                                } else super.visit(key,value);
                            }
                        };
                    }
                };
            }
        };
        reader.accept(convert,0);
        return writer.toByteArray();
    }

    public static byte[] adaptJar(byte[] verifiedBytes) throws IOException {
        Map<String,byte[]> entries = new TreeMap<>();
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(verifiedBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") || name.equals("META-INF/MANIFEST.MF"))) continue;
                byte[] bytes = zip.readNBytes(64 * 1024 * 1024 + 1);
                expanded += bytes.length;
                if (bytes.length > 64 * 1024 * 1024 || expanded > 512L * 1024 * 1024) throw new IOException("Oversized PZ3D archive");
                if (name.endsWith(".class")) {
                    if (!name.startsWith("com/pavelvoronin/pz3d/")) throw new IOException("Foreign class in PZ3D archive: " + name);
                    bytes = adaptClass(bytes);
                }
                if (entries.putIfAbsent(name,bytes) != null) throw new IOException("Duplicate JAR entry: " + name);
            }
        }
        if (!entries.containsKey("com/pavelvoronin/pz3d/Main.class") || !entries.containsKey("com/pavelvoronin/pz3d/Patches.class")) throw new IOException("Not a PZ3D JAR");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey()); item.setTime(0);
                zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
