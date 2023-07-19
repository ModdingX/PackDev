package org.moddingx.packdev.platform;

import org.apache.commons.io.input.CountingInputStream;
import org.gradle.api.Project;
import org.moddingx.packdev.cache.PackDevCache;
import org.moddingx.packdev.util.curse.MurmurHasher;
import org.moddingx.packdev.util.hash.ComputedHash;
import org.moddingx.packdev.util.hash.HashAlgorithm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public abstract class BaseModFile implements ModFile {
    
    protected final Project project;
    private final PackDevCache cache;

    protected BaseModFile(Project project, PackDevCache cache) {
        this.project = project;
        this.cache = cache;
    }

    // Some unique string within the platform
    protected abstract String fileKey();

    @Override
    public final InputStream openStream() throws IOException {
        Path cachePath = this.cache.getCachePath("files", this.fileKey() + ".jar");
        if (!Files.isRegularFile(cachePath) || Files.size(cachePath) <= 0) {
            try(InputStream remote = this.openRemoteStream()) {
                Files.copy(remote, cachePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(cachePath);
            }
        }
        return Files.newInputStream(cachePath);
    }
    
    protected InputStream openRemoteStream() throws IOException {
        return ModFile.super.openStream();
    }

    @Override
    public final Map<String, ComputedHash> hashes(Set<String> hashes) throws NoSuchAlgorithmException, IOException {
        Map<String, ComputedHash> result = new HashMap<>();
        Set<String> hashesLeft = new HashSet<>();
        for (String key : hashes) {
            String algorithm = key.toLowerCase(Locale.ROOT);
            ComputedHash hash = this.cache.getHash(this.fileKey(), algorithm);
            if (hash != null) {
                result.put(algorithm, hash);
            } else {
                hashesLeft.add(algorithm);
            }
        }
        if (!hashesLeft.isEmpty()) {
            Map<String, ComputedHash> computed = this.computeHashes(Collections.unmodifiableSet(hashesLeft));
            for (Map.Entry<String, ComputedHash> entry : computed.entrySet()) {
                String algorithm = entry.getKey().toLowerCase(Locale.ROOT);
                if (hashesLeft.contains(algorithm)) {
                    ComputedHash hash = entry.getValue();
                    this.cache.updateHash(this.fileKey(), algorithm, hash);
                    result.put(algorithm, hash);
                    hashesLeft.remove(algorithm);
                }
            }
        }
        if (!hashesLeft.isEmpty()) {
            throw new NoSuchAlgorithmException("Failed to compute all hashes. Missing: " + String.join(", ", hashesLeft) + " for file: " + this.fileKey());
        }
        return Collections.unmodifiableMap(result);
    }
    
    protected Map<String, ComputedHash> computeHashes(Set<String> hashes) throws NoSuchAlgorithmException, IOException {
        if (hashes.isEmpty()) return Map.of();
        Map<String, ComputedHash> result = new HashMap<>();
        Map<HashAlgorithm, MessageDigest> digests = new HashMap<>();
        for (String hash : hashes) {
            HashAlgorithm algorithm = HashAlgorithm.get(hash);
            if (algorithm != null) {
                digests.put(algorithm, algorithm.createDigest());
            } else if (!Objects.equals(hash, "fingerprint") && !Objects.equals(hash, "size")) {
                throw new NoSuchAlgorithmException("Can't compute " + hash + " hash for file: " + this.fileKey());
            }
        }
        try (InputStream in = this.openStream()) {
            InputStream current = in;
            CountingInputStream counter = null;
            if (hashes.contains("size")) {
                counter = new CountingInputStream(current);
                current = counter;
            }
            for (MessageDigest digest : digests.values()) {
                current = new DigestInputStream(current, digest);
            }
            if (hashes.contains("fingerprint")) {
                byte[] data = current.readAllBytes();
                result.put("fingerprint", ComputedHash.of(((long) MurmurHasher.hash(data)) & 0xFFFFFFFFl, 32));
            } else {
                byte[] buffer = new byte[8192];
                //noinspection StatementWithEmptyBody
                while (current.read(buffer) >= 0);
            }
            if (counter != null) {
                result.put("size", ComputedHash.ofSignedLong(counter.getByteCount()));
            }
            for (Map.Entry<HashAlgorithm, MessageDigest> entry : digests.entrySet()) {
                result.put(entry.getKey().id, ComputedHash.of(entry.getValue().digest(), entry.getKey().bits));
            }
        }
        return result;
    }
}
