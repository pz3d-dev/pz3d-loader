package com.pavelvoronin.pz3dLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import net.bytebuddy.jar.asm.*;
/** Build/installation check; does not execute the mod or load game classes. */
public final class Inspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected PZ3D JAR path");
        Path jar = Path.of(args[0]);
        byte[] input = Files.readAllBytes(jar);
        String key;
        try(var stream = Inspect.class.getResourceAsStream("/pz3d-public-key.hex")) { key = new String(stream.readAllBytes(),StandardCharsets.US_ASCII).trim(); }
        String hash = JarSignature.verify(input,Path.of(jar + ".zbs"),JarSignature.publicKey(key));
        try(var archive = new java.util.jar.JarFile(jar.toFile())) {
            if(archive.getManifest()!=null && "1".equals(archive.getManifest().getMainAttributes().getValue("Pz3dLoader-Bootstrap"))) {
                jar=com.pavelvoronin.pz3dLoader.bootstrap.Main.extractVerifiedCore(jar);
                input=Files.readAllBytes(jar);
            }
        }
        byte[] converted = Pz3dAdapter.adaptJar(input);
        int[] patches = {0}; int classes = 0;
        try(var zip = new ZipInputStream(new ByteArrayInputStream(converted))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) if(entry.getName().endsWith(".class")) {
                classes++;
                new ClassReader(zip.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                        if(descriptor.equals("Lcom/pavelvoronin/pz3dLoader/Patch;")) patches[0]++;
                        return null;
                    }
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        }
        System.out.println("PASS official signature sha256=" + hash + "; adapted classes=" + classes + "; patch declarations=" + patches[0]);
    }
}
