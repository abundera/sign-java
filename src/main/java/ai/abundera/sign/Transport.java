package ai.abundera.sign;

import java.io.IOException;

/**
 * Sends one {@link RawRequest} and returns its {@link RawResponse}.
 *
 * <p>The production default ({@link JdkHttpTransport}) wraps
 * {@code java.net.http.HttpClient} — no third-party HTTP library. Tests
 * inject a fake implementation to exercise retry/backoff/idempotency
 * deterministically, without a real socket, mirroring the injectable
 * transport in the Ruby, Go, and TypeScript reference SDKs.
 */
@FunctionalInterface
public interface Transport {
    RawResponse send(RawRequest request) throws IOException;
}
