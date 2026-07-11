package ai.abundera.sign;

import ai.abundera.sign.model.ArchiveEnvelopeResult;
import ai.abundera.sign.model.AuditTrail;
import ai.abundera.sign.model.CreateWebhookResult;
import ai.abundera.sign.model.DeleteTemplateResult;
import ai.abundera.sign.model.DeleteWebhookResult;
import ai.abundera.sign.model.Envelope;
import ai.abundera.sign.model.EnvelopeCreateParams;
import ai.abundera.sign.model.EnvelopeCreateResult;
import ai.abundera.sign.model.EnvelopeList;
import ai.abundera.sign.model.EnvelopeUpdateResult;
import ai.abundera.sign.model.EvidenceChecklist;
import ai.abundera.sign.model.PingWebhookResult;
import ai.abundera.sign.model.RemindResult;
import ai.abundera.sign.model.Template;
import ai.abundera.sign.model.TemplateCategories;
import ai.abundera.sign.model.TemplateCreateParams;
import ai.abundera.sign.model.TemplateList;
import ai.abundera.sign.model.VerifyResult;
import ai.abundera.sign.model.VoidEnvelopeResult;
import ai.abundera.sign.model.WebhookDelivery;
import ai.abundera.sign.model.WebhookDeliveryList;
import ai.abundera.sign.model.WebhookList;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Synchronous client for the Abundera Sign REST API (v1).
 *
 * <pre>{@code
 * Client client = Client.builder("abnd_sign_...").build();
 * Envelope env = client.createEnvelope(
 *     EnvelopeCreateParams.builder("nda-mutual")
 *         .signers(List.of(new SignerCreate("a@b.co", "Ana")))
 *         .build());
 * env.id(); // "env_3f9a2c10"
 * }</pre>
 *
 * <p>Every method returns a typed model (or {@code byte[]} for the PDF
 * endpoint). Non-2xx responses raise {@link AbunderaApiException}. {@code
 * new Client(...)} is production-safe with zero extra configuration:
 * retries, jitter, timeouts, and connection reuse are all correct by
 * default — see {@link #builder(String)}.
 */
public final class Client {

    public static final String DEFAULT_BASE_URL = "https://sign.abundera.ai";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** 429 + 5xx + 409 (a retryable write conflict) — never any other 4xx. */
    static final Set<Integer> RETRY_STATUS = Set.of(409, 429, 500, 502, 503, 504);

    /** Full-jitter backoff: delay = rand(0, min(CAP, BASE * 2**attempt)), in milliseconds. */
    static final double BACKOFF_BASE_MS = 250.0;
    static final double BACKOFF_CAP_MS = 20_000.0;

    private final String apiKey;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final String userAgent;
    private final Transport transport;
    private final DoubleConsumer sleeper;
    private final ObjectMapper mapper;

    private Client(Builder b) {
        if (b.apiKey == null || b.apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        this.apiKey = b.apiKey;
        this.baseUrl = stripTrailingSlash(b.baseUrl);
        this.requestTimeout = b.requestTimeout;
        this.maxRetries = b.maxRetries;
        this.userAgent = b.userAgent;
        this.transport = b.transport != null
                ? b.transport
                : new JdkHttpTransport(b.connectTimeout, b.requestTimeout);
        this.sleeper = b.sleeper != null ? b.sleeper : Client::defaultSleep;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static void defaultSleep(double millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep((long) millis, (int) ((millis - (long) millis) * 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    public String baseUrl() {
        return baseUrl;
    }

    // --- Envelopes ----------------------------------------------------------

    /**
     * An explicit idempotency key makes retries idempotent across process
     * restarts; omit it (use {@link #createEnvelope(EnvelopeCreateParams)})
     * and one is auto-generated, stable across this call's internal retries.
     */
    public EnvelopeCreateResult createEnvelope(EnvelopeCreateParams params) {
        return createEnvelope(params, null);
    }

    public EnvelopeCreateResult createEnvelope(EnvelopeCreateParams params, String idempotencyKey) {
        JsonNode data = requestJson("POST", "/api/envelopes", params, null, idempotencyKey);
        return convert(data, EnvelopeCreateResult.class);
    }

    public EnvelopeList listEnvelopes() {
        return listEnvelopes(Map.of());
    }

    public EnvelopeList listEnvelopes(Map<String, Object> query) {
        JsonNode data = requestJson("GET", "/api/envelopes", null, query, null);
        return convert(data, EnvelopeList.class);
    }

    public Envelope getEnvelope(String envelopeId) {
        JsonNode data = requestJson("GET", "/api/envelopes/" + escape(envelopeId), null, null, null);
        return convert(data, Envelope.class);
    }

    public EnvelopeUpdateResult updateEnvelope(String envelopeId, boolean allowNegotiation) {
        JsonNode data = requestJson("PATCH", "/api/envelopes/" + escape(envelopeId),
                Map.of("allow_negotiation", allowNegotiation), null, null);
        return convert(data, EnvelopeUpdateResult.class);
    }

    public VoidEnvelopeResult voidEnvelope(String envelopeId) {
        JsonNode data = requestJson("POST", "/api/void-envelope",
                Map.of("envelope_id", envelopeId), null, null);
        return convert(data, VoidEnvelopeResult.class);
    }

    /** {@code action} is {@code "archive"} or {@code "unarchive"}. */
    public ArchiveEnvelopeResult archiveEnvelope(String envelopeId, String action) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("envelope_id", envelopeId);
        body.put("action", action);
        JsonNode data = requestJson("POST", "/api/archive-envelope", body, null, null);
        return convert(data, ArchiveEnvelopeResult.class);
    }

    /** Re-send the signing email to all pending signers (or one, if {@code signerId} is present). */
    public RemindResult remind(String envelopeId, Optional<String> signerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("envelope_id", envelopeId);
        signerId.ifPresent(id -> body.put("signer_id", id));
        JsonNode data = requestJson("POST", "/api/remind", body, null, null);
        return convert(data, RemindResult.class);
    }

    public AuditTrail getAuditTrail(String envelopeId) {
        JsonNode data = requestJson("GET", "/api/envelopes/" + escape(envelopeId) + "/audit", null, null, null);
        return convert(data, AuditTrail.class);
    }

    public EvidenceChecklist getEvidenceChecklist(String envelopeId) {
        JsonNode data = requestJson("GET", "/api/envelopes/" + escape(envelopeId) + "/evidence", null, null, null);
        return convert(data, EvidenceChecklist.class);
    }

    /** Court-ready declaration PDF (Professional+). Returns the raw PDF bytes. */
    public byte[] getDeclarationPdf(String envelopeId) {
        return requestBinary("GET", "/api/envelopes/" + escape(envelopeId) + "/declaration");
    }

    // --- Templates ------------------------------------------------------------

    public TemplateList listTemplates() {
        return listTemplates(Map.of());
    }

    public TemplateList listTemplates(Map<String, Object> query) {
        JsonNode data = requestJson("GET", "/api/templates", null, query, null);
        return convert(data, TemplateList.class);
    }

    public Template getTemplate(String templateId) {
        JsonNode data = requestJson("GET", "/api/templates/" + escape(templateId), null, null, null);
        return convert(data, Template.class);
    }

    public TemplateCategories listTemplateCategories() {
        JsonNode data = requestJson("GET", "/api/templates/categories", null, null, null);
        return convert(data, TemplateCategories.class);
    }

    public Template createTemplate(TemplateCreateParams params) {
        return createTemplate(params, null);
    }

    public Template createTemplate(TemplateCreateParams params, String idempotencyKey) {
        JsonNode data = requestJson("POST", "/api/templates", params, null, idempotencyKey);
        return convert(data, Template.class);
    }

    public Template updateTemplate(String templateId, TemplateCreateParams params) {
        JsonNode data = requestJson("PUT", "/api/templates/" + escape(templateId), params, null, null);
        return convert(data, Template.class);
    }

    public DeleteTemplateResult deleteTemplate(String templateId) {
        JsonNode data = requestJson("DELETE", "/api/templates/" + escape(templateId), null, null, null);
        return convert(data, DeleteTemplateResult.class);
    }

    // --- Webhooks (account-level) ----------------------------------------------

    public WebhookList listWebhooks() {
        JsonNode data = requestJson("GET", "/api/webhooks/manage", null, null, null);
        return convert(data, WebhookList.class);
    }

    /** The returned secret ({@code whsec_...}) is shown in full only once — store it. */
    public CreateWebhookResult createWebhook(String url) {
        return createWebhook(url, null, null, null);
    }

    public CreateWebhookResult createWebhook(String url, List<String> events, String secret) {
        return createWebhook(url, events, secret, null);
    }

    public CreateWebhookResult createWebhook(String url, List<String> events, String secret, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (events != null) {
            body.put("events", events);
        }
        if (secret != null) {
            body.put("secret", secret);
        }
        JsonNode data = requestJson("POST", "/api/webhooks/manage", body, null, idempotencyKey);
        return convert(data, CreateWebhookResult.class);
    }

    public DeleteWebhookResult deleteWebhook(String webhookId) {
        JsonNode data = requestJson("DELETE", "/api/webhooks/manage",
                Map.of("webhook_id", webhookId), null, null);
        return convert(data, DeleteWebhookResult.class);
    }

    public PingWebhookResult pingWebhook(String webhookId) {
        JsonNode data = requestJson("POST", "/api/webhooks/manage/ping",
                Map.of("webhook_id", webhookId), null, null);
        return convert(data, PingWebhookResult.class);
    }

    public WebhookDeliveryList listWebhookDeliveries() {
        return listWebhookDeliveries(Map.of());
    }

    public WebhookDeliveryList listWebhookDeliveries(Map<String, Object> query) {
        JsonNode data = requestJson("GET", "/api/webhooks/manage/deliveries", null, query, null);
        return convert(data, WebhookDeliveryList.class);
    }

    public WebhookDelivery getWebhookDelivery(String deliveryId) {
        JsonNode data = requestJson("GET", "/api/webhooks/manage/deliveries/" + escape(deliveryId), null, null, null);
        return convert(data, WebhookDelivery.class);
    }

    // --- Public verification -----------------------------------------------

    /** Public document verification by verification id ({@code vrf_...}) or SHA-256 hash. */
    public VerifyResult verifyDocument(Optional<String> id, Optional<String> hash) {
        if (id.isEmpty() && hash.isEmpty()) {
            throw new IllegalArgumentException("verifyDocument requires id or hash");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        id.ifPresent(v -> query.put("id", v));
        hash.ifPresent(v -> query.put("hash", v));
        JsonNode data = requestJson("GET", "/api/verify", null, query, null);
        return convert(data, VerifyResult.class);
    }

    // --- Raw escape hatch ----------------------------------------------------

    /**
     * Low-level request that still runs auth + retry + envelope unwrap, for
     * endpoints ahead of this SDK's typed coverage. {@code path} may be
     * {@code /api/...} (rewritten to {@code /api/v1/...}) or already
     * {@code /api/v1/...}.
     */
    public JsonNode request(String method, String path, Object body, Map<String, Object> query,
                             String idempotencyKey) {
        return requestJson(method, path, body, query, idempotencyKey);
    }

    // --- Internals -------------------------------------------------------------

    private <T> T convert(JsonNode data, Class<T> type) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        return mapper.convertValue(data, type);
    }

    private JsonNode requestJson(String method, String path, Object body, Map<String, Object> query,
                                  String idempotencyKeyOverride) {
        byte[] jsonBody = body == null ? null : serialize(body);
        RawResponse res = executeWithRetry(method, path, jsonBody, query, "application/json", idempotencyKeyOverride);
        return parseJsonSuccess(res);
    }

    private byte[] requestBinary(String method, String path) {
        RawResponse res = executeWithRetry(method, path, null, null, "application/pdf", null);
        return res.body() == null ? new byte[0] : res.body();
    }

    private byte[] serialize(Object body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode parseJsonSuccess(RawResponse res) {
        byte[] body = res.body();
        if (body == null || body.length == 0) {
            return null;
        }
        JsonNode parsed;
        try {
            parsed = mapper.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // v1 envelope: { ok, data, request_id } -> return the inner payload.
        if (parsed.isObject() && parsed.has("ok")) {
            return parsed.get("data");
        }
        return parsed;
    }

    private RawResponse executeWithRetry(String method, String path, byte[] jsonBody, Map<String, Object> query,
                                          String accept, String idempotencyKeyOverride) {
        URI uri = buildUri(path, query);

        // Auto-generate a stable idempotency key for mutating POSTs so an internal
        // retry (network blip / 5xx) replays the server's cached response instead
        // of creating a second resource. Generated ONCE, outside the retry loop, so
        // every attempt of this logical call carries the same key. Callers may
        // override to make retries idempotent across process restarts. Non-POST
        // requests never carry an idempotency key, even if one was supplied.
        String idempotencyKey = null;
        if ("POST".equals(method)) {
            idempotencyKey = idempotencyKeyOverride != null ? idempotencyKeyOverride : "abnd-" + UUID.randomUUID();
        }

        IOException lastNetworkError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            RawResponse res;
            try {
                res = performOnce(method, uri, jsonBody, accept, idempotencyKey);
            } catch (IOException e) {
                lastNetworkError = e;
                if (attempt >= maxRetries) {
                    throw new AbunderaNetworkException("network error calling Abundera Sign API", e);
                }
                sleepMillis(backoffMillis(attempt));
                continue;
            }

            int code = res.status();
            if (code >= 200 && code < 300) {
                return res;
            }

            if (RETRY_STATUS.contains(code) && attempt < maxRetries) {
                // STANDARD §2: wait = max(computed_backoff, Retry-After) — the
                // server's requested delay wins only when it exceeds our backoff.
                Double retryAfterMs = retryAfterMillis(res);
                sleepMillis(retryAfterMs != null
                    ? Math.max(retryAfterMs, backoffMillis(attempt))
                    : backoffMillis(attempt));
                continue;
            }

            throw buildApiError(res);
        }

        if (lastNetworkError != null) {
            throw new AbunderaNetworkException("network error calling Abundera Sign API", lastNetworkError);
        }
        throw new AbunderaSignException("unreachable");
    }

    private RawResponse performOnce(String method, URI uri, byte[] jsonBody, String accept, String idempotencyKey)
            throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("User-Agent", userAgent);
        headers.put("Accept", accept);
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        if (jsonBody != null) {
            headers.put("Content-Type", "application/json");
        }
        return transport.send(new RawRequest(method, uri, headers, jsonBody));
    }

    private AbunderaApiException buildApiError(RawResponse res) {
        String requestId = res.header("x-request-id");
        String code = "http_error";
        String message = "HTTP " + res.status();
        byte[] body = res.body();
        if (body != null && body.length > 0) {
            try {
                JsonNode j = mapper.readTree(body);
                JsonNode e = (j.isObject() && j.has("error") && j.get("error").isObject()) ? j.get("error") : j;
                if (e.isObject()) {
                    if (e.hasNonNull("code")) {
                        code = e.get("code").asText();
                    }
                    if (e.hasNonNull("message")) {
                        message = e.get("message").asText();
                    }
                }
                if (j.isObject() && j.hasNonNull("request_id")) {
                    requestId = j.get("request_id").asText();
                }
            } catch (IOException ignored) {
                // non-JSON error body -> keep the HTTP-status defaults
            }
        }
        return new AbunderaApiException(res.status(), code, message, requestId);
    }

    private URI buildUri(String path, Map<String, Object> query) {
        // Every product endpoint lives under /api/v1/*. Callers pass /api/*;
        // rewrite centrally so the per-method paths stay readable.
        String v1 = (path.startsWith("/api/") && !path.startsWith("/api/v1/"))
                ? "/api/v1/" + path.substring("/api/".length())
                : path;
        StringBuilder sb = new StringBuilder(baseUrl).append(v1);
        String qs = buildQueryString(query);
        if (!qs.isEmpty()) {
            sb.append('?').append(qs);
        }
        return URI.create(sb.toString());
    }

    /** Drops nil values so they never become query-string entries. */
    private String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(String.valueOf(e.getValue())));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** encodeURIComponent-equivalent for a single path segment. */
    private static String escape(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Double retryAfterMillis(RawResponse res) {
        String raw = res.header("retry-after");
        if (raw == null) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(raw.trim());
            return seconds > 0 ? seconds * 1000.0 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Full jitter (AWS Architecture Blog canonical): spread retries randomly
     * across [0, capped-exponential] so many clients don't retry in lockstep.
     */
    static double backoffMillis(int attempt) {
        double ceiling = Math.min(BACKOFF_CAP_MS, BACKOFF_BASE_MS * Math.pow(2, attempt));
        return Math.random() * ceiling;
    }

    private void sleepMillis(double millis) {
        sleeper.accept(millis);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Fluent builder for {@link Client}. */
    public static final class Builder {
        private final String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String userAgent = "abundera-sign-java/" + Version.VERSION;
        private Transport transport;
        private DoubleConsumer sleeper;

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /** Injectable HTTP transport for tests. Defaults to real {@code java.net.http.HttpClient} over TLS. */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /** Injectable sleep function (milliseconds) so tests don't actually sleep through the backoff schedule. */
        public Builder sleeper(DoubleConsumer sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public Client build() {
            return new Client(this);
        }
    }
}
