package com.pavelvoronin.pz3dLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import net.bytebuddy.jar.asm.*;

public final class LoaderTests {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if (!value) throw new AssertionError(message); }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) throws Exception {
        checks++;
        try { action.run(); } catch (SecurityException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) throws Exception {
        check(!GameCompatibility.supports(41,78) && !GameCompatibility.supports(42,20),"old game versions rejected");
        check(GameCompatibility.supports(42,21) && GameCompatibility.supports(42,22),"supported game version boundary");
        check(Version.CURRENT.equals("1.0.1"),"loader display version");
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] publicBytes = pair.getPublic().getEncoded();
        var key = JarSignature.publicKey(HexFormat.of().formatHex(Arrays.copyOfRange(publicBytes,publicBytes.length-32,publicBytes.length)));
        byte[] jar = "fixture jar bytes".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        var signer = Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate());
        signer.update(("ZBS:" + JarSignature.AUTHOR + ":" + hash).getBytes(StandardCharsets.UTF_8));
        String signed = "ZBS\nSteamID64:" + JarSignature.AUTHOR + "\nSignature:" + HexFormat.of().formatHex(signer.sign()) + "\n";
        Path sidecar = Files.createTempFile("pz3d-sign-test-", ".zbs");
        try {
            Files.writeString(sidecar,signed);
            check(hash.equals(JarSignature.verify(jar,sidecar,key)),"valid signature");
            rejects(() -> JarSignature.verify(new byte[]{1},sidecar,key));
            rejects(() -> JarSignature.verify(jar,sidecar,KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()));
            Files.writeString(sidecar,signed.replace(JarSignature.AUTHOR,"76561198008387518"));
            rejects(() -> JarSignature.verify(jar,sidecar,key));
            Files.writeString(sidecar,signed + "extra");
            rejects(() -> JarSignature.verify(jar,sidecar,key));
            Files.writeString(sidecar,signed.replace("\n","\r\n"));
            check(hash.equals(JarSignature.verify(jar,sidecar,key)),"CRLF signature");
        } finally { Files.delete(sidecar); }
        Path other = agentJar("other.Agent"), zb = agentJar("me.zed_0xff.zombie_buddy.Agent");
        try {
            String a = "-javaagent:" + other, b = "-javaagent:" + zb + "=policy=allow-all";
            check(!StartupAgents.hasZombieBuddy(List.of(a)),"unrelated agent");
            check(StartupAgents.hasZombieBuddy(List.of(a,b)),"ZB later");
            check(StartupAgents.hasZombieBuddy(List.of(b,a)),"ZB earlier");
        } finally { Files.delete(other); Files.delete(zb); }
        byte[] input = fixtureAdvice();
        byte[] output = Pz3dAdapter.adaptClass(input);
        boolean[] seen = {false,false};
        new ClassReader(output).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) {
                if (desc.equals("Lcom/pavelvoronin/pz3dLoader/Patch;")) seen[0] = true;
                return null;
            }
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                        if (!descriptor.equals("Lnet/bytebuddy/asm/Advice$OnMethodEnter;")) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visit(String name,Object value) {
                                if (name.equals("skipOn") && value.equals(Type.getType("Lnet/bytebuddy/asm/Advice$OnNonDefaultValue;"))) seen[1] = true;
                            }
                        };
                    }
                };
            }
        },0);
        check(seen[0],"class annotation adaptation"); check(seen[1],"skipOn boolean conversion");
        byte[] cacheFixture=UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        Path cached=AdaptedCache.store(cacheFixture);
        try {
            check(cached.equals(AdaptedCache.store(cacheFixture)),"same adapted bytes reuse one cache entry");
            Files.write(cached,new byte[]{0});
            rejects(()->AdaptedCache.store(cacheFixture));
        } finally { Files.deleteIfExists(cached); }
        check(Panel.buddyBottom(1920,16,64,0,false,List.of(),s->s.length()*8)==64,"empty Buddy panel keeps icon clearance");
        check(Panel.buddyBottom(240,20,64,0,true,List.of("first-long-mod","second-long-mod","third-long-mod"),s->s.length()*9)==100,"wrapped Buddy lines and warning reserve full height");
        check(Panel.buddyBottom(2560,32,128,16,false,List.of("pz3d"),s->s.length()*16)==128,"high resolution Buddy icon clearance");
        check(Panel.wrap("PZ3D could not load - see console.txt",120,s->s.length()*8).size()>1,"status wraps in narrow windows");
        check(StartupAgents.hasZombieBuddy(List.of("-agentlib:zbNative")),"original Windows native bridge");
        Path selection=Files.createTempDirectory("pz3dLoader-selection-");
        Path expected=selection.resolve("mod.info"), core=selection.resolve("core.jar"), different=selection.resolve("other.jar");
        try {
            Files.writeString(expected,"javaPkgName=com.pavelvoronin.pz3dLoader.bootstrap\njavaJarFile=core.jar\n");
            Files.write(core,new byte[]{0});Files.write(different,new byte[]{1});
            check(SelectedMod.resolve(selection,null).jar().equals(core.toRealPath()),"selected version core");
            check(SelectedMod.resolve(null,selection).bootstrap(),"common-only bootstrap");
            Files.writeString(expected,"javaPkgName=com.pavelvoronin.pz3d\njavaJarFile=../escape.jar\n");
            rejects(()->SelectedMod.resolve(selection,null));
        } finally {Files.delete(expected);Files.delete(core);Files.delete(different);Files.delete(selection);}
        Path folders=Files.createTempDirectory("pz3dLoader-folders-");
        Path version=Files.createDirectory(folders.resolve("42")), common=Files.createDirectory(folders.resolve("common"));
        Path vi=version.resolve("mod.info"), ci=common.resolve("mod.info"), vj=version.resolve("core.jar"), cj=common.resolve("core.jar");
        String metadata="javaPkgName=com.pavelvoronin.pz3dLoader.bootstrap\njavaJarFile=core.jar\n";
        try {
            Files.writeString(vi,metadata); Files.writeString(ci,metadata);
            Files.write(vj,new byte[]{0}); Files.write(cj,new byte[]{1});
            check(SelectedMod.resolve(version,common).jar().equals(vj.toRealPath()),"version takes priority over common");
            Files.delete(vj);
            check(SelectedMod.resolve(version,common).jar().equals(cj.toRealPath()),"common fallback");
            Files.delete(ci);
            check(SelectedMod.resolve(version,common).jar().equals(cj.toRealPath()),"version metadata with common core");
            Files.delete(vi); Files.delete(cj); Files.writeString(ci,metadata); Files.write(vj,new byte[]{0});
            check(SelectedMod.resolve(version,common).jar().equals(vj.toRealPath()),"common metadata with version core");
            Files.writeString(ci,"javaPkgName=com.pavelvoronin.pz3dLoader.bootstrap\njavaJarFile="+vj.toString()+"\n");
            rejects(()->SelectedMod.resolve(version,common));
        } finally {
            for(Path file:List.of(vi,ci,vj,cj,version,common,folders))Files.deleteIfExists(file);
        }
        System.out.println("PASS " + checks + " loader checks");
    }
    private static Path agentJar(String premain) throws Exception {
        Path path = Files.createTempFile("renamed agent ",".jar");
        var manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Premain-Class",premain);
        try(var stream = new JarOutputStream(Files.newOutputStream(path),manifest)) {}
        return path;
    }
    private static byte[] fixtureAdvice() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"com/pavelvoronin/pz3d/Fixture",null,"java/lang/Object",null);
        var patch = writer.visitAnnotation("Lme/zed_0xff/zombie_buddy/Patch;",true);
        patch.visit("className","zombie.Fixture"); patch.visit("methodName","value"); patch.visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"enter","()Z",null,null);
        var annotation = method.visitAnnotation("Lme/zed_0xff/zombie_buddy/Patch$OnEnter;",true);
        annotation.visit("skipOn",true); annotation.visitEnd();
        method.visitCode(); method.visitInsn(Opcodes.ICONST_1); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(1,0); method.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
}
