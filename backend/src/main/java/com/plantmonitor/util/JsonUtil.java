package com.plantmonitor.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Pequenos helpers repetidos em vários controllers. */
public class JsonUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static Double asDouble(Object o) {
        return (o instanceof Number) ? ((Number) o).doubleValue() : null;
    }

    public static Integer asInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : null;
    }

    public static Long asLong(Object o) {
        return (o instanceof Number) ? ((Number) o).longValue() : null;
    }

    public static boolean asBool(Object o) {
        return Boolean.TRUE.equals(o);
    }

    /** Timestamp UTC em milissegundos, terminando em "Z" — o que o frontend espera de eventos ao vivo. */
    public static String nowIso() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }

    public static String randomHex(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
