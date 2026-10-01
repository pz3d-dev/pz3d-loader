import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Isolate Byte Buddy so merely placing our agent on the classpath cannot change ZombieBuddy. */
public final class Relocate {
    private static final String FROM="net/bytebuddy/", TO="com/pavelvoronin/pz3dLoader/internal/bytebuddy/";
    private static String replace(String value) {
        return value.replace(FROM,TO).replace(FROM.replace('/','.'),TO.replace('/','.'));
    }
    public static void main(String[] args)throws Exception {
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        Map<String,byte[]> entries=new TreeMap<>();
        Remapper remapper=new Remapper(Opcodes.ASM9) {
            @Override public String map(String name){return name.startsWith(FROM)?TO+name.substring(FROM.length()):name;}
            @Override public Object mapValue(Object value){return value instanceof String text?replace(text):super.mapValue(value);}
        };
        try(JarFile jar=new JarFile(input.toFile())) {
            for(var list=jar.entries();list.hasMoreElements();) {
                JarEntry entry=list.nextElement();if(entry.isDirectory())continue;
                byte[] bytes;try(var stream=jar.getInputStream(entry)){bytes=stream.readAllBytes();}
                String name=replace(entry.getName());
                if(name.endsWith("module-info.class"))continue;
                if(name.endsWith(".class")) {
                    ClassWriter writer=new ClassWriter(0);
                    new ClassReader(bytes).accept(new ClassRemapper(writer,remapper),0);
                    bytes=writer.toByteArray();
                } else if(name.startsWith("META-INF/services/"))bytes=replace(new String(bytes,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                if(entries.putIfAbsent(name,bytes)!=null)throw new IllegalStateException("Relocation collision: "+name);
            }
        }
        try(var zip=new JarOutputStream(Files.newOutputStream(output))) {
            for(var entry:entries.entrySet()) {
                JarEntry item=new JarEntry(entry.getKey());item.setTime(1767225600000L);
                zip.putNextEntry(item);zip.write(entry.getValue());zip.closeEntry();
            }
        }
    }
}
