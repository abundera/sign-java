package ai.abundera.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.abundera.sign.model.Envelope;
import ai.abundera.sign.model.EnvelopeCreateParams;
import ai.abundera.sign.model.EnvelopeCreateResult;
import ai.abundera.sign.model.SignerCreate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Client tests via an injected {@link Transport} — no network. The transport
 * records each request and returns queued fake responses, so we exercise URL
 * building, the {@code /api -> /api/v1} rewrite, envelope unwrap, error
 * mapping, retry with Retry-After, idempotency, and query-param cleaning
 * deterministically. Mirrors {@code sdks/ruby/test/test_client.rb} test-for-test.
 */
class ClientTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** Records every request sent; pops the next queued response (or throws a queued IOException). */
    static final class RecordingTransport implements Transport {
        final Deque<Object> queue;
        final List<RawRequest> requests = new ArrayList<>();

        RecordingTransport(List<Object> responses) {
            this.queue = new ArrayDeque<>(responses);
        }

        @Override
        public RawResponse send(RawRequest request) throws IOException {
            requests.add(request);
            Object next = queue.poll();
            if (next == null) {
                throw new IllegalStateException("no more queued responses");
            }
            if (next instanceof IOException) {
                throw (IOException) next;
            }
            return (RawResponse) next;
        }
    }

    private static RawResponse okResponse(Map<String, Object> data) {
        return okResponse(data, "req_1", Map.of());
    }

    private static RawResponse okResponse(Map<String, Object> data, String requestId, Map<String, String> extraHeaders) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("ok", true);
            envelope.put("data", data);
            envelope.put("request_id", requestId);
            return new RawResponse(200, extraHeaders, OM.writeValueAsBytes(envelope));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static RawResponse errorResponse(int status, String code, String message, String requestId,
                                              Map<String, String> headers) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("ok", false);
            envelope.put("error", error);
            envelope.put("request_id", requestId);
            return new RawResponse(status, headers, OM.writeValueAsBytes(envelope));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Object[] newClient(List<Object> responses) {
        RecordingTransport transport = new RecordingTransport(responses);
        Client client = Client.builder("abnd_sign_test")
                .transport(transport)
                .sleeper(ms -> { })
                .build();
        return new Object[]{client, transport};
    }

    private static String header(RawRequest req, String name) {
        for (Map.Entry<String, String> e : req.headers().entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    // --- basic wiring -----------------------------------------------------------

    @Test
    void unwrapsTheEnvelopeAndSetsAuth() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_9"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        Envelope result = client.getEnvelope("env_9");

        assertEquals("env_9", result.id());
        assertEquals("Bearer abnd_sign_test", header(t.requests.get(0), "Authorization"));
    }

    @Test
    void rewritesApiToApiV1() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_9"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.getEnvelope("env_9");

        assertEquals("/api/v1/envelopes/env_9", t.requests.get(0).uri().getPath());
    }

    @Test
    void urlEncodesPathSegments() {
        Object[] cx = newClient(List.of(okResponse(Map.of())));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.getTemplate("a b/c");

        String raw = t.requests.get(0).uri().getRawPath();
        assertEquals("/api/v1/templates/a%20b%2Fc", raw);
    }

    @Test
    void dropsNilQueryParams() {
        Map<String, Object> listData = Map.of("envelopes", List.of(), "pagination",
                Map.of("limit", 20, "total", 0));
        Object[] cx = newClient(List.of(okResponse(listData)));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("status", "sent");
        query.put("template", null);
        query.put("limit", 10);
        client.listEnvelopes(query);

        String q = t.requests.get(0).uri().getRawQuery();
        assertTrue(q.contains("status=sent"));
        assertTrue(q.contains("limit=10"));
        assertTrue(!q.contains("template"));
    }

    @Test
    void postSerializesJsonBody() throws IOException {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_new"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        EnvelopeCreateParams params = EnvelopeCreateParams.builder("nda-mutual")
                .signers(List.of(new SignerCreate("a@b.co", null)))
                .build();
        client.createEnvelope(params);

        JsonNode sent = OM.readTree(t.requests.get(0).body());
        assertEquals("nda-mutual", sent.get("template_id").asText());
        assertEquals("application/json", header(t.requests.get(0), "Content-Type"));
    }

    @Test
    void mapsErrorEnvelopeToApiException() {
        RawResponse resp = errorResponse(404, "not_found", "Envelope not found", "req_err", Map.of());
        Object[] cx = newClient(List.of(resp));
        Client client = (Client) cx[0];

        AbunderaApiException err = assertThrows(AbunderaApiException.class, () -> client.getEnvelope("missing"));
        assertEquals(404, err.status());
        assertEquals("not_found", err.code());
        assertEquals("req_err", err.requestId());
        assertTrue(err.getMessage().contains("Envelope not found"));
    }

    @Test
    void retriesOn429ThenSucceeds() {
        RawResponse retryResp = errorResponse(429, "rate_limited", "slow down", null, Map.of("retry-after", "0"));
        Object[] cx = newClient(List.of(retryResp, okResponse(Map.of("id", "env_ok"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        Envelope result = client.getEnvelope("env_ok");

        assertEquals("env_ok", result.id());
        assertEquals(2, t.requests.size());
    }

    @Test
    void givesUpAfterMaxRetries() {
        List<Object> responses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            responses.add(errorResponse(503, "unavailable", "down", null, Map.of()));
        }
        Object[] cx = newClient(responses);
        Client client = (Client) cx[0];

        AbunderaApiException err = assertThrows(AbunderaApiException.class, () -> client.getEnvelope("x"));
        assertEquals(503, err.status());
    }

    @Test
    void retriesNetworkErrors() {
        List<Object> responses = List.of(new IOException("connection reset"), okResponse(Map.of("id", "env_net")));
        Object[] cx = newClient(responses);
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        Envelope result = client.getEnvelope("env_net");

        assertEquals("env_net", result.id());
        assertEquals(2, t.requests.size());
    }

    @Test
    void voidAndArchiveUseActionEndpoints() throws IOException {
        Object[] cx = newClient(List.of(
                okResponse(Map.of("status", "voided")),
                okResponse(Map.of("status", "archived"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.voidEnvelope("env_1");
        client.archiveEnvelope("env_1", "archive");

        assertEquals("/api/v1/void-envelope", t.requests.get(0).uri().getPath());
        JsonNode archiveBody = OM.readTree(t.requests.get(1).body());
        assertEquals("archive", archiveBody.get("action").asText());
    }

    @Test
    void verifyDocumentRequiresIdOrHash() {
        Object[] cx = newClient(List.of());
        Client client = (Client) cx[0];
        assertThrows(IllegalArgumentException.class,
                () -> client.verifyDocument(Optional.empty(), Optional.empty()));
    }

    @Test
    void binaryAcceptReturnsRawBytes() {
        byte[] pdfBytes = "%PDF-1.7\nbytes".getBytes(StandardCharsets.US_ASCII);
        RawResponse pdf = new RawResponse(200, Map.of(), pdfBytes);
        Object[] cx = newClient(List.of(pdf));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        byte[] result = client.getDeclarationPdf("env_1");

        assertTrue(java.util.Arrays.equals(pdfBytes, result));
        assertEquals("application/pdf", header(t.requests.get(0), "Accept"));
    }

    @Test
    void missingApiKeyRaises() {
        assertThrows(IllegalArgumentException.class, () -> Client.builder("").build());
    }

    // --- idempotency ------------------------------------------------------------

    @Test
    void postAutoGeneratesIdempotencyKey() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_1"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.createEnvelope(EnvelopeCreateParams.builder("nda-mutual").build());

        String key = header(t.requests.get(0), "Idempotency-Key");
        assertNotNull(key);
        assertTrue(key.startsWith("abnd-"), "expected an auto key, got " + key);
    }

    @Test
    void getSendsNoIdempotencyKey() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_1"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.getEnvelope("env_1");

        assertNull(header(t.requests.get(0), "Idempotency-Key"));
    }

    @Test
    void idempotencyKeyIsStableAcrossRetries() {
        RawResponse retryResp = errorResponse(503, "x", "y", null, Map.of());
        Object[] cx = newClient(List.of(retryResp, okResponse(Map.of("id", "env_1"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.createEnvelope(EnvelopeCreateParams.builder("nda-mutual").build());

        assertEquals(2, t.requests.size());
        String key0 = header(t.requests.get(0), "Idempotency-Key");
        String key1 = header(t.requests.get(1), "Idempotency-Key");
        assertEquals(key0, key1, "retry must reuse the same idempotency key");
    }

    @Test
    void distinctCallsGetDistinctKeys() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "a")), okResponse(Map.of("id", "b"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        EnvelopeCreateParams params = EnvelopeCreateParams.builder("t").build();
        client.createEnvelope(params);
        client.createEnvelope(params);

        assertNotEquals(header(t.requests.get(0), "Idempotency-Key"), header(t.requests.get(1), "Idempotency-Key"));
    }

    @Test
    void callerCanOverrideIdempotencyKey() {
        Object[] cx = newClient(List.of(okResponse(Map.of("id", "env_1"))));
        Client client = (Client) cx[0];
        RecordingTransport t = (RecordingTransport) cx[1];

        client.createEnvelope(EnvelopeCreateParams.builder("t").build(), "my-key-123");

        assertEquals("my-key-123", header(t.requests.get(0), "Idempotency-Key"));
    }

    // --- backoff jitter -----------------------------------------------------------

    @Test
    void backoffIsJitteredAndCapped() {
        double b = Client.backoffMillis(0);
        assertTrue(b >= 0 && b <= 250, "attempt 0 within [0, base]");

        List<Double> draws = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            draws.add(Client.backoffMillis(30));
        }
        assertTrue(draws.stream().allMatch(d -> d >= 0 && d <= Client.BACKOFF_CAP_MS));
        assertTrue(draws.stream().distinct().count() > 1, "jitter must randomize the delay");
    }

    // --- typed model roundtrip ----------------------------------------------------

    @Test
    void createEnvelopeReturnsTypedResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "env_new");
        data.put("status", "sent");
        data.put("template_id", "nda-mutual");
        Object[] cx = newClient(List.of(okResponse(data)));
        Client client = (Client) cx[0];

        EnvelopeCreateResult result = client.createEnvelope(EnvelopeCreateParams.builder("nda-mutual").build());

        assertEquals("env_new", result.id());
        assertEquals("sent", result.status());
        assertEquals("nda-mutual", result.templateId());
    }
}
