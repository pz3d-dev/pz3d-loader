package com.pavelvoronin.pz3dLoader;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.jar.*;
import java.io.*;

/** Workshop-delivered updates. Never replace classes underneath a running JVM. */
public final class SelfUpdater {
    public static volatile String pendingVersion="";
    private static boolean checked;
    private SelfUpdater() {}
    static PublicKey key() throws Exception {
        try(var stream=SelfUpdater.class.getResourceAsStream("/pz3d-public-key.hex")){
            if(stream==null)throw new SecurityException("Missing update trust key");
            return JarSignature.publicKey(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII).trim());
        }
    }
    static String version(Path jar) throws Exception {
        try(var file=new JarFile(jar.toFile())){
            var attributes=file.getManifest().getMainAttributes();
            if(!"com.pavelvoronin.pz3dLoader.Agent".equals(attributes.getValue("Premain-Class")))throw new SecurityException("Not a Pz3dLoader update");
            String version=attributes.getValue("Implementation-Version");
            if(version==null || !version.matches("[0-9]{1,6}(\\.[0-9]{1,6}){1,2}"))throw new SecurityException("Invalid update version");
            return version;
        }
    }
    static boolean newer(String candidate,String current) {
        String[] a=candidate.split("\\."),b=current.split("\\.");
        for(int i=0;i<Math.max(a.length,b.length);i++){
            int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;
            if(x!=y)return x>y;
        }
        return false;
    }
    static String verified(Path candidate,PublicKey key) throws Exception {
        if(Files.size(candidate)>32L*1024*1024)throw new SecurityException("Update too large");
        JarSignature.verify(Files.readAllBytes(candidate),Path.of(candidate+".zbs"),key);
        return version(candidate);
    }
    static boolean stage(Path current,Path candidate,PublicKey key) throws Exception {
        String next=verified(candidate,key);
        if(!newer(next,version(current)))return false;
        Path pending=Path.of(current+".new"),signature=Path.of(pending+".zbs");
        Path temp=Files.createTempFile(current.getParent(),"pz3dLoader-update-",".jar");
        try {
            Files.copy(candidate,temp,StandardCopyOption.REPLACE_EXISTING);
            Files.copy(Path.of(candidate+".zbs"),Path.of(temp+".zbs"),StandardCopyOption.REPLACE_EXISTING);
            verified(temp,key); // Verify the exact staged bytes, not just the source.
            Files.move(Path.of(temp+".zbs"),signature,StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp,pending,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);Files.deleteIfExists(Path.of(temp+".zbs"));}
        return true;
    }
    static boolean apply(Path current,PublicKey key) throws Exception {
        Path pending=Path.of(current+".new");
        if(!Files.isRegularFile(pending)){
            Path signature=Path.of(pending+".zbs");
            if(Files.isRegularFile(signature)){
                JarSignature.verify(Files.readAllBytes(current),signature,key);
                Files.move(signature,Path.of(current+".zbs"),StandardCopyOption.REPLACE_EXISTING);
            }
            return false;
        }
        String next=verified(pending,key);
        if(!newer(next,version(current)))return false;
        Files.copy(current,Path.of(current+".bak"),StandardCopyOption.REPLACE_EXISTING);
        if(Files.isRegularFile(Path.of(current+".zbs")))Files.copy(Path.of(current+".zbs"),Path.of(current+".bak.zbs"),StandardCopyOption.REPLACE_EXISTING);
        // Keep the previous JAR in place if the atomic replacement fails.
        Files.move(pending,current,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Files.move(Path.of(pending+".zbs"),Path.of(current+".zbs"),StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
    public static synchronized void discover(Object selected) {
        if(checked || selected==null)return;
        checked=true;
        try {
            Path current=Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(current))return;
            PublicKey key=key();
            // Retry an interrupted deferred update even if the Workshop copy vanished.
            Path pending=Path.of(current+".new");
            for(String getter:new String[]{"getVersionDir","getCommonDir"}){
                String directory=(String)selected.getClass().getMethod(getter).invoke(selected);
                if(directory==null)continue;
                if(!Files.isDirectory(Path.of(directory)))continue;
                Path root=Path.of(directory).toRealPath();
                Path candidate=root.resolve("media/pz3d/loader/pz3dLoader.jar");
                if(!Files.isRegularFile(candidate))continue;
                if(!candidate.toRealPath().startsWith(root))throw new SecurityException("Update escapes selected mod");
                if(Files.isRegularFile(pending) && !newer(verified(candidate,key),verified(pending,key)))continue;
                stage(current,candidate,key);
            }
            if(Files.isRegularFile(pending) && newer(verified(pending,key),Version.CURRENT)){
                pendingVersion=version(pending);
                Path helper=Files.createTempDirectory("pz3dLoader-update-").resolve("helper.jar");
                Files.copy(current,helper);
                String executable=System.getProperty("os.name").startsWith("Windows")?"javaw.exe":"java";
                Path java=Path.of(System.getProperty("java.home"),"bin",executable);
                new ProcessBuilder(java.toString(),"-cp",helper.toString(),SelfUpdater.class.getName(),Long.toString(ProcessHandle.current().pid()),current.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of(current+".update.log").toFile())).start();
                System.out.println("[pz3d Loader] Verified update "+pendingVersion+" staged; applies after the game exits");
            }
        }catch(Exception error){System.err.println("[pz3d Loader] Update unavailable; keeping installed version: "+error);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected parent process and installed JAR");
        Path current=Path.of(args[1]).toAbsolutePath();
        ProcessHandle parent=ProcessHandle.of(Long.parseLong(args[0])).orElse(null);
        if(parent!=null)parent.onExit().join();
        try(var channel=FileChannel.open(Path.of(current+".update.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=channel.tryLock()){
            if(lock!=null)System.out.println("[pz3d Loader] Deferred update applied="+apply(current,key()));
        }
        // The running helper JAR may still be locked on Windows; leave only this
        // bounded temporary file for the OS temp cleanup if deletion is unavailable.
        Path helper=Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(helper.getFileName().toString().equals("helper.jar") && helper.getParent().getFileName().toString().startsWith("pz3dLoader-update-")){
            try{Files.deleteIfExists(helper);Files.deleteIfExists(helper.getParent());}catch(IOException ignored){}
        }
    }
}
