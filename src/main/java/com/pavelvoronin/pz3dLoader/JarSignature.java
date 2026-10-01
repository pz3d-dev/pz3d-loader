package com.pavelvoronin.pz3dLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/** Offline verification of the existing PZ3D/ZombieBuddy detached signature format. */
public final class JarSignature {
    public static final String AUTHOR = "76561198008387517";
    private JarSignature() {}

    public static PublicKey publicKey(String rawHex) throws Exception {
        if (!rawHex.matches("[0-9a-fA-F]{64}")) throw new SecurityException("Invalid Ed25519 public key");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
            HexFormat.of().parseHex("302a300506032b6570032100" + rawHex)));
    }

    public static String verify(byte[] jar, Path sidecar, PublicKey trustedKey) throws Exception {
        if (Files.size(sidecar) > 512) throw new SecurityException("Signature sidecar too large");
        String value = Files.readString(sidecar, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String prefix = "ZBS\nSteamID64:" + AUTHOR + "\nSignature:";
        if (!value.startsWith(prefix)) throw new SecurityException("Invalid signature header or author");
        String hex = value.substring(prefix.length());
        if (hex.endsWith("\n")) hex = hex.substring(0, hex.length() - 1);
        if (!hex.matches("[0-9a-fA-F]{128}")) throw new SecurityException("Invalid signature encoding");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(trustedKey);
        verifier.update(("ZBS:" + AUTHOR + ":" + hash).getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(HexFormat.of().parseHex(hex))) throw new SecurityException("PZ3D signature rejected");
        return hash;
    }
}
