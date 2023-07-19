package org.moddingx.packdev.util.hash;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;

public final class ComputedHash {
    
    private final int bits;
    private final BigInteger value;
    
    @Nullable private String hexString = null;

    private ComputedHash(int bits, BigInteger value) {
        this.bits = bits;
        BigInteger mask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        this.value = value.abs().and(mask);
    }

    public String hexDigest() {
        if (this.hexString == null) {
            int digits = (this.bits % 4) == 0 ? this.bits / 4 : (this.bits / 4) + 1;
            this.hexString = String.format("%0" + digits + "x", this.value).toLowerCase(Locale.ROOT);
        }
        return this.hexString;
    }
    
    public long longValue() {
        return this.value.longValueExact();
    }
    
    public BigInteger numeric() {
        return this.value;
    }
    
    public BigInteger store() {
        return BigInteger.valueOf(this.bits).or(this.value.shiftLeft(32));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ComputedHash hash)) {
            return false;
        } else {
            return this.bits == hash.bits && Objects.equals(this.value, hash.value);
        }
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

    public static ComputedHash ofSignedLong(long value) {
        return of(value, 64);
    }
    
    public static ComputedHash of(String hexDigest) {
        return of(hexDigest, hexDigest.length() * 4);
    }
    
    public static ComputedHash of(String hexDigest, int bits) {
        return new ComputedHash(bits, new BigInteger(hexDigest, 16));
    }
    
    public static ComputedHash of(byte[] data, int bits) {
        return new ComputedHash(bits, new BigInteger(1, data));
    }
    
    public static ComputedHash of(long value, int bits) {
        if (value < 0) {
            return new ComputedHash(bits, new BigInteger(Long.toUnsignedString(value)));
        } else {
            return new ComputedHash(bits, BigInteger.valueOf(value));
        }
    }
    
    public static ComputedHash load(BigInteger storedHash) {
        int bits = storedHash.abs().and(BigInteger.valueOf(0xFFFFFFFFl)).intValueExact();
        BigInteger data = storedHash.abs().shiftRight(32);
        return new ComputedHash(bits, data);
    }
}
