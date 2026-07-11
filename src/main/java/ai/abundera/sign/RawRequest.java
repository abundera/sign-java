package ai.abundera.sign;

import java.net.URI;
import java.util.Map;

/**
 * A fully-built outbound HTTP request, transport-agnostic.
 *
 * <p>Kept intentionally simple (no dependency on {@code java.net.http} types)
 * so tests can inject a fake {@link Transport} without constructing real JDK
 * HTTP objects — the same shape as the Ruby test suite's callable transport
 * that records {@code (method, path, query, body, headers)}.
 */
public record RawRequest(String method, URI uri, Map<String, String> headers, byte[] body) {
}
