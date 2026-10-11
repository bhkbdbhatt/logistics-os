package io.logisticsos.observability;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Trace/correlation identity helpers. {@code traceparent} follows W3C Trace Context:
 * {@code 00-<32-hex trace-id>-<16-hex span-id>-01}. Random hex keeps the module dependency-free
 * (no ULID/avro required); ids are 128-bit unique, not temporal.
 */
public final class Trace {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TRACEPARENT_VERSION = "00";
    private static final String TRACEPARENT_FLAGS = "01";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Trace() {
    }

    public static String newTraceId() {
        return randomHex(16);
    }

    public static String newSpanId() {
        return randomHex(8);
    }

    /** Correlation id: a trace id is a fine correlation key (spans chain via parent-id). */
    public static String newCorrelationId() {
        return newTraceId();
    }

    public static String traceparent(String traceId, String spanId) {
        return TRACEPARENT_VERSION + "-" + traceId + "-" + spanId + "-" + TRACEPARENT_FLAGS;
    }

    public static boolean isValidTraceId(String traceId) {
        return traceId != null && traceId.length() == 32 && isHex(traceId);
    }

    public static boolean isValidSpanId(String spanId) {
        return spanId != null && spanId.length() == 16 && isHex(spanId);
    }

    private static String randomHex(int bytes) {
        byte[] raw = new byte[bytes];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }
}