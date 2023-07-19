package org.moddingx.packdev.util.curse;

// Curse murmur hasher
public class MurmurHasher {

    public static int hash(byte[] data) {
        int pos = 0;
        byte[] cleanData = new byte[data.length];
        for (byte b : data) {
            if (b != 9 && b != 10 && b != 13 && b != 32) {
                cleanData[pos++] = b;
            }
        }

        int magic = 0x5BD1E995;
        int hash = 1 ^ pos;
        for (int idx = 0; idx < pos; idx += 4) {
            int left = pos - idx;
            if (left >= 4) {
                int le = ((int) cleanData[idx]) & 0xFF;
                le |= (((int) cleanData[idx + 1]) & 0xFF) << 8;
                le |= (((int) cleanData[idx + 2]) & 0xFF) << 16;
                le |= (((int) cleanData[idx + 3]) & 0xFF) << 24;
                le *= magic;
                le ^= (le >>> 24);
                le *= magic;
                hash *= magic;
                hash ^= le;
            } else if (left >= 1) {
                for (int off = 0; off < left; off++) {
                    hash ^= (cleanData[idx + off] << (8 * off));
                }
                hash *= magic;
            }
        }
        hash ^= (hash >>> 13);
        hash *= magic;
        hash ^= (hash >>> 15);
        return hash;
    }
}
