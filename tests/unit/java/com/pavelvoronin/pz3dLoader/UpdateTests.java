package com.pavelvoronin.pz3dLoader;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.TimeUnit;

public final class UpdateTests {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void sign(Path jar,PrivateKey key)throws Exception{
        var signer=Signature.getInstance("Ed25519");signer.initSign(key);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        signer.update(("ZBS:"+JarSignature.AUTHOR+":"+hash).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Files.writeString(Path.of(jar+".zbs"),"ZBS\nSteamID64:"+JarSignature.AUTHOR+"\nSignature:"+HexFormat.of().formatHex(signer.sign())+"\n");
    }
    static Path jar(Path root,String name,String version,PrivateKey key)throws Exception{
        Path file=root.resolve(name);var manifest=new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Premain-Class","com.pavelvoronin.pz3dLoader.Agent");
        manifest.getMainAttributes().putValue("Implementation-Version",version);
        try(var out=new JarOutputStream(Files.newOutputStream(file),manifest)){}
        sign(file,key);return file;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0 && args[0].equals("pause")){Thread.sleep(2500);return;}
        Path root=Files.createTempDirectory("pz3dLoader-update-test-");
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path current=jar(root,"installed.jar","1.0",keys.getPrivate());
        Path next=jar(root,"candidate.jar","1.1",keys.getPrivate());
        check(SelfUpdater.stage(current,next,keys.getPublic()),"newer update staged");
        check(SelfUpdater.version(current).equals("1.0"),"running JAR remains unchanged");
        Files.writeString(Path.of(current+".new.zbs"),"tampered");
        try{SelfUpdater.apply(current,keys.getPublic());throw new AssertionError("tampered update accepted");}catch(SecurityException expected){}
        check(SelfUpdater.version(current).equals("1.0"),"invalid update preserves installed JAR");
        SelfUpdater.stage(current,next,keys.getPublic());
        check(SelfUpdater.apply(current,keys.getPublic()),"deferred update applied");
        check(SelfUpdater.version(current).equals("1.1") && SelfUpdater.version(Path.of(current+".bak")).equals("1.0"),"new version and backup");
        SelfUpdater.verified(current,keys.getPublic());
        check(!SelfUpdater.stage(current,next,keys.getPublic()),"same version ignored");
        check(!SelfUpdater.stage(current,Path.of(current+".bak"),keys.getPublic()),"downgrade ignored");
        sign(next,KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate());
        try{SelfUpdater.stage(current,next,keys.getPublic());throw new AssertionError("wrong signer accepted");}catch(SecurityException expected){}
        // Run the real deferred worker in a separate JVM and wait for a parent process.
        current=jar(root,"process.jar","1.0",keys.getPrivate());sign(next,keys.getPrivate());
        SelfUpdater.stage(current,next,keys.getPublic());
        Path helper=root.resolve("worker.jar");
        try(var output=new JarOutputStream(Files.newOutputStream(helper))){
            for(Class<?> type:List.of(SelfUpdater.class,JarSignature.class,Version.class)){
                String entry=type.getName().replace('.','/')+".class";
                output.putNextEntry(new JarEntry(entry));
                try(var input=type.getResourceAsStream("/"+entry)){input.transferTo(output);}output.closeEntry();
            }
            byte[] encoded=keys.getPublic().getEncoded();output.putNextEntry(new JarEntry("pz3d-public-key.hex"));
            output.write(HexFormat.of().formatHex(Arrays.copyOfRange(encoded,encoded.length-32,encoded.length)).getBytes());output.closeEntry();
        }
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Process parent=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),UpdateTests.class.getName(),"pause").start();
        Process worker=new ProcessBuilder(java,"-cp",helper.toString(),SelfUpdater.class.getName(),Long.toString(parent.pid()),current.toString()).redirectErrorStream(true).redirectOutput(root.resolve("worker.log").toFile()).start();
        try{
            Thread.sleep(400);check(worker.isAlive() && SelfUpdater.version(current).equals("1.0"),"worker waits for running game");
            check(parent.waitFor(10,TimeUnit.SECONDS) && worker.waitFor(10,TimeUnit.SECONDS),"worker exits after parent");
            check(worker.exitValue()==0 && SelfUpdater.version(current).equals("1.1"),"real worker installed update");
        }finally{if(parent.isAlive())parent.destroyForcibly();if(worker.isAlive())worker.destroyForcibly();}
        System.out.println("PASS: signed updates, wrong signer/tampering, no downgrade, backup, deferred worker and process exit: "+root);
    }
}
