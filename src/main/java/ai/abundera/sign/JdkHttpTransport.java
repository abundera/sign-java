package ai.abundera.sign;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Default {@link Transport}: real TLS over {@code java.net.http.HttpClient}.
 * Connection reuse (keep-alive) is on by default in the JDK client. Connect
 * and per-request timeouts are configured independently, per STANDARD.md §2.
 */
final class JdkHttpTransport implements Transport {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    JdkHttpTransport(Duration connectTimeout, Duration requestTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public RawResponse send(RawRequest request) throws IOException {
        HttpRequest.BodyPublisher publisher = request.body() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.body());

        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(requestTimeout)
                .method(request.method(), publisher);
        request.headers().forEach(builder::header);

        try {
            HttpResponse<byte[]> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values));
                }
            });
            return new RawResponse(response.statusCode(), headers, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for HTTP response", e);
        }
    }
}
