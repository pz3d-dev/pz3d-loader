package com.pavelvoronin.pz3dLoader;

import java.nio.file.*;
import java.util.*;
import net.bytebuddy.asm.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Add this verified loader to the native Host JVM, preserving every native argument. */
public final class HostServerLauncher {
    static AsmVisitorWrapper substitution()throws NoSuchMethodException{
        return MemberSubstitution.strict().constructor(isDeclaredBy(ProcessBuilder.class).and(takesArguments(List.class)))
            .replaceWith(HostServerLauncher.class.getMethod("builder",List.class)).on(named("launchServer"));
    }
    public static ProcessBuilder builder(List<String> nativeArguments){
        try{
            Path loader=Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ProcessBuilder(arguments(nativeArguments,loader));
        }catch(Exception error){
            System.err.println("[pz3d Loader] Host server retains its native launch: "+error);
            return new ProcessBuilder(nativeArguments);
        }
    }
    static List<String> arguments(List<String> nativeArguments,Path loader){
        var command=new ArrayList<>(nativeArguments);
        int entry=command.indexOf("zombie.network.GameServer");
        if(entry<1||!Files.isRegularFile(loader)||!loader.toString().endsWith(".jar"))return command;
        if(command.stream().anyMatch(s->s.startsWith("-javaagent:")&&Path.of(s.substring(11).split("=",2)[0]).getFileName().toString().equalsIgnoreCase("pz3dLoader.jar")))return command;
        command.add(entry,"-javaagent:"+loader.toAbsolutePath().normalize());
        if(command.stream().noneMatch(s->s.startsWith("-Dpz3d.server.optimizations=")))command.add(entry,"-Dpz3d.server.optimizations=true");
        return command;
    }
    private HostServerLauncher(){}
}