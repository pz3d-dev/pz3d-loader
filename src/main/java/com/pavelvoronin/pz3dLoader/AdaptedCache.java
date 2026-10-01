package com.pavelvoronin.pz3dLoader;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Content-addressed cache: Windows keeps instrumentation search JARs open until JVM termination. */
final class AdaptedCache {
    private AdaptedCache() {}
    static Path store(byte[] bytes) throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path root=Path.of(System.getProperty("java.io.tmpdir"),"pz3dLoader-adapted");
        Files.createDirectories(root);
        Path destination=root.resolve(hash+".jar");
        if(!Files.exists(destination)) {
            Path staging=Files.createTempFile(root,"pending-",".jar");
            try {
                Files.write(staging,bytes);
                try { Files.move(staging,destination); }
                catch(FileAlreadyExistsException concurrentWriter) { /* Validate the winning entry below. */ }
            } finally { Files.deleteIfExists(staging); }
        }
        if(!MessageDigest.isEqual(bytes,Files.readAllBytes(destination)))throw new SecurityException("Corrupted PZ3D adapter cache: "+destination);
        return destination;
    }
}
