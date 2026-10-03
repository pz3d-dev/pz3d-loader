package com.pavelvoronin.pz3dLoader;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MultiplayerLoaderTest {
    public static class NativeHostFixture { public ProcessBuilder launchServer(List<String> command){return new ProcessBuilder(command);} }
    @Test void nativeHostSubstitution()throws Exception{
        var type=new net.bytebuddy.ByteBuddy().redefine(NativeHostFixture.class).visit(HostServerLauncher.substitution()).make()
            .load(getClass().getClassLoader(),net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.CHILD_FIRST).getLoaded();
        var command=List.of("java","zombie.network.GameServer","-coop");
        var builder=(ProcessBuilder)type.getMethod("launchServer",List.class).invoke(type.getConstructor().newInstance(),command);
        assertEquals(command,builder.command()); // In Gradle the loader location is a classes directory; no child is started.
    }
    @Test void optionalServerOnly(){
        String before=System.getProperty("pz3d.server.optimizations");
        try{System.setProperty("pz3d.server.optimizations","true");assertTrue(RuntimeRole.optionalServer(true));assertFalse(RuntimeRole.optionalServer(false));
            System.setProperty("pz3d.server.optimizations","false");assertFalse(RuntimeRole.optionalServer(true));}
        finally{if(before==null)System.clearProperty("pz3d.server.optimizations");else System.setProperty("pz3d.server.optimizations",before);}
    }
    @Test void roles(){
        assertTrue(RuntimeRole.retainLocal(false,true,true));
        assertFalse(RuntimeRole.retainLocal(true,false,true));
        assertFalse(RuntimeRole.retainLocal(false,false,false));
        assertFalse(RuntimeRole.retainLocal(false,true,false));
        assertEquals("com.pavelvoronin.pz3d.ServerMain",RuntimeRole.entry(true));
        assertEquals("com.pavelvoronin.pz3dserver.ServerPatches",RuntimeRole.patches(true));
    }
    @Test void nativeHostArguments()throws Exception{
        Path directory=Files.createTempDirectory("pz3d-host-test-");Path loader=directory.resolve("pz3dLoader.jar");Files.write(loader,new byte[]{1});
        try{
            var original=List.of("java","-Xmx2048m","-Djava.awt.headless=true","zombie.network.GameServer","-coop","-servername","Fixture");
            var changed=HostServerLauncher.arguments(original,loader);
            assertEquals(original,changed.stream().filter(s->!s.startsWith("-javaagent:")&&!s.startsWith("-Dpz3d.server.optimizations=")).toList());
            assertTrue(changed.indexOf("-javaagent:"+loader)<changed.indexOf("zombie.network.GameServer"));
            assertEquals(changed,HostServerLauncher.arguments(changed,loader));
            assertEquals(List.of("java","OtherMain"),HostServerLauncher.arguments(List.of("java","OtherMain"),loader));
            assertEquals(original,HostServerLauncher.arguments(original,directory));
        }finally{Files.deleteIfExists(loader);Files.deleteIfExists(directory);}
    }
}