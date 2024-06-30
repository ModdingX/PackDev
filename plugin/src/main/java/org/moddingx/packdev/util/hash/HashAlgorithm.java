package org.moddingx.packdev.util.hash;

import jakarta.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Some common hashes, that should always be supported
// They all work through MessageDigest
public enum HashAlgorithm {
    
    SHA1("sha1", 160, "SHA1"),
    SHA256("sha256", 256, "SHA256"),
    SHA512("sha512", 512, "SHA512"),
    MD5("md5", 128, "MD5");
    
    private static final Map<String, HashAlgorithm> valueMap = Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(h -> h.id, Function.identity()));
    
    public final String id;
    public final int bits;
    private final String digestName;

    HashAlgorithm(String id, int bits, @Nullable String digestName) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.bits = Math.max(bits, 0);
        this.digestName = digestName;
    }
    
    @Nullable
    public static HashAlgorithm get(String key) {
        return valueMap.getOrDefault(key.toLowerCase(Locale.ROOT), null);
    }
    
    public MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(this.digestName);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Can't compute " + this.id + " hash: Missing provider", e);
        }
    }
}
