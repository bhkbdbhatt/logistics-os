package io.logisticsos.events;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Identifier utilities for the Logistics OS event backbone.
 * Event ids are ULIDs: 48-bit timestamp (epoch millis) + 80 random bits, Crockford base32.
 * Lexicographic sort order equals time order, which keeps log/topic ordering readable.
 */
public final class Ulid {

    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    /** Generate a ULID string for the given epoch-millis timestamp. */
    public static String generate(long timestampMillis) {
        if (timestampMillis < 0) {
            throw new IllegalArgumentException("timestampMillis must be >= 0");
        }
        // 128-bit big-endian integer: high 48 bits timestamp, low 80 bits entropy.
        byte[] value = new byte[16];
        for (int i = 5; i >= 0; i--) {
            value[i] = (byte) (timestampMillis >>> (8 * (5 - i)));
        }
        byte[] entropy = new byte[10];
        RANDOM.nextBytes(entropy);
        System.arraycopy(entropy, 0, value, 6, 10);

        BigInteger n = new BigInteger(1, value);
        char[] out = new char[26];
        for (int i = 25; i >= 0; i--) {
            out[i] = CROCKFORD[n.intValue() & 0x1F];
            n = n.shiftRight(5);
        }
        return new String(out);
    }

    /** Generate a ULID string using the current wall clock. */
    public static String generate() {
        return generate(System.currentTimeMillis());
    }

    /** True when the string is a syntactically valid ULID. */
    public static boolean isValid(String value) {
        if (value == null || value.length() != 26) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (CROCKFORD_INDEX.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Parse the embedded timestamp (epoch millis) out of a ULID, or -1 when invalid. */
    public static long timestampOf(String value) {
        if (!isValid(value)) {
            return -1;
        }
        long ts = 0;
        for (int i = 0; i < 10; i++) {
            ts = (ts << 5) | CROCKFORD_INDEX.indexOf(value.charAt(i));
        }
        return ts >> 2;
    }

    private static final String CROCKFORD_INDEX = new String(CROCKFORD);
}