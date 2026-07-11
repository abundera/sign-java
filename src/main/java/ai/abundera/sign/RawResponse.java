package ai.abundera.sign;

import java.util.Map;

/**
 * A raw HTTP response. Header lookups are case-insensitive at the call site
 * ({@link Client} always queries lower-cased header names); implementations
 * of {@link Transport} should store header keys lower-cased.
 */
public record RawResponse(int status, Map<String, String> headers, byte[] body) {

    /** Case-insensitive header lookup; returns {@code null} if absent. */
    public String header(String name) {
        return headers == null ? null : headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }
}
