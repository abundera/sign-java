# Abundera Sign — Java SDK

Official Java SDK for the [Abundera Sign](https://sign.abundera.ai) REST API (v1).

## Install

Not yet on Maven Central. Install via [JitPack](https://jitpack.io) (add the repository, then the dependency):

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
  <groupId>com.github.abundera</groupId>
  <artifactId>sign-java</artifactId>
  <version>main-SNAPSHOT</version>
</dependency>
```

## First call

```java
import ai.abundera.sign.Client;
import ai.abundera.sign.model.*;
import java.util.List;

Client client = Client.builder("abnd_sign_...").build();

EnvelopeCreateResult env = client.createEnvelope(
    EnvelopeCreateParams.builder("nda-mutual")
        .signers(List.of(new SignerCreate("ana@example.com", "Ana")))
        .build());

System.out.println(env.id()); // "env_3f9a2c10"
```

Every method returns a typed model. Non-2xx responses raise
`AbunderaApiException` (`status()`, `code()`, `requestId()`). `Client.builder(key).build()`
is production-safe with zero extra configuration — retries, jitter, timeouts,
and connection reuse are all correct by default.

## Webhooks

Abundera Sign signs every webhook delivery per the
[Standard Webhooks](https://www.standardwebhooks.com/) spec. Verify the raw
body — before any framework JSON-parses it — with `Webhook.verify`:

```java
import ai.abundera.sign.Webhook;
import ai.abundera.sign.WebhookVerificationException;

// Spark/Javalin/plain Servlet: read the raw request body as a String or
// byte[] BEFORE any JSON deserialization touches it.
String rawBody = readRawRequestBody(request);

try {
    Webhook.verify(
        request.getHeader("webhook-id"),
        request.getHeader("webhook-timestamp"),
        request.getHeader("webhook-signature"),
        rawBody,
        System.getenv("ABUNDERA_WEBHOOK_SECRET"), // whsec_...
        null);
    // signature verified — now it's safe to JSON-parse rawBody
} catch (WebhookVerificationException e) {
    // reject with 400 — do not process the payload
}
```

Pass a `whpk_...` public key as the sixth argument (or in place of the
secret) to additionally/instead verify the `v1a` Ed25519 signature — the
non-repudiation scheme available on Business+. During secret rotation, pass
both the old and new secret and any one match verifies.

## Reliability

- Retries on `409`, `429`, `5xx`, and connection errors — never any other `4xx`.
- Honors `Retry-After`; otherwise full-jitter exponential backoff (`rand(0, min(20s, 250ms * 2^attempt))`), capped at 3 retries by default.
- Every internally-retried mutating `POST` carries a stable `Idempotency-Key`, auto-generated (`abnd-<uuid>`) or caller-supplied.
- Separate connect and request timeouts (`Client.builder(key).connectTimeout(...).requestTimeout(...)`), both 30s by default.
- Zero third-party HTTP dependency — built on `java.net.http.HttpClient`.

## Errors

Every non-2xx response raises `AbunderaApiException`, carrying:

| Method | Meaning |
|---|---|
| `status()` | HTTP status code |
| `code()` | stable, machine-readable error code from the v1 error envelope |
| `getMessage()` | human-readable message |
| `requestId()` | request id — quote this in a support ticket |

Network failures (after retries are exhausted) raise `AbunderaNetworkException`
with the original `IOException` as `getCause()`. Both extend the unchecked
`AbunderaSignException`.

## Requirements

Java 17 or newer. No dependencies beyond `jackson-databind` at runtime.

## Escape hatch

Endpoints ahead of this SDK's typed coverage are reachable via the low-level
`client.request(method, path, body, query, idempotencyKey)`, which still runs
auth, retry, and envelope unwrap — it returns the raw Jackson `JsonNode`.

## License

MIT — see [LICENSE](LICENSE).
