package com.pavelvoronin.pz3dLoader;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.jar.*;

/** Offline packaging only. Uses the existing author identity and never modifies the input core. */
public final class Bundle {
    public static void main(String[] args) throws Exception {
        Path core=Path.of(args[0]), template=Path.of(args[1]), output=Path.of(args[2]), key=Path.of(args[3]);
        if(Files.exists(output)||Files.exists(Path.of(output+".zbs")))throw new IllegalArgumentException("Output already exists");
        byte[] bytes=Files.readAllBytes(core);
        String publicKey;
        try(var stream=Bundle.class.getResourceAsStream("/pz3d-public-key.hex")){publicKey=new String(stream.readAllBytes(),StandardCharsets.US_ASCII).trim();}
        JarSignature.verify(bytes,Path.of(core+".zbs"),JarSignature.publicKey(publicKey));
        var signer=Signature.getInstance("Ed25519");
        signer.initSign(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(key))));
        Map<String,byte[]> entries=new TreeMap<>();
        try(JarFile jar=new JarFile(template.toFile())) {
            for(var items=jar.entries();items.hasMoreElements();) {
                var item=items.nextElement();
                if(!item.isDirectory())try(var stream=jar.getInputStream(item)){entries.put(item.getName(),stream.readAllBytes());}
            }
        }
        entries.put("engine/core.jar",bytes);
        entries.put("engine/core.jar.zbs",Files.readAllBytes(Path.of(core+".zbs")));
        entries.put("META-INF/MANIFEST.MF","Manifest-Version: 1.0\r\nPz3dLoader-Bootstrap: 1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        try(var jar=new JarOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))) {
            for(var entry:entries.entrySet()) {
                var item=new JarEntry(entry.getKey());item.setTime(1767225600000L);
                jar.putNextEntry(item);jar.write(entry.getValue());jar.closeEntry();
            }
        }
        byte[] bundle=Files.readAllBytes(output);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle));
        signer.update(("ZBS:"+JarSignature.AUTHOR+":"+hash).getBytes(StandardCharsets.US_ASCII));
        Path sidecar=Path.of(output+".zbs");
        Files.writeString(sidecar,"ZBS\nSteamID64:"+JarSignature.AUTHOR+"\nSignature:"+HexFormat.of().formatHex(signer.sign())+"\n",StandardOpenOption.CREATE_NEW);
        JarSignature.verify(bundle,sidecar,JarSignature.publicKey(publicKey));
        com.pavelvoronin.pz3dLoader.bootstrap.Main.extractVerifiedCore(output);
        System.out.println("Verified bootstrap bundle: "+output+", sha256="+hash);
    }
}
