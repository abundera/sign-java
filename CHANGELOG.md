# Changelog

All notable changes to this SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-07-10

### Added

- Initial release of the official Java SDK for Abundera Sign.
- `Client`: bearer auth, `/api` -> `/api/v1` rewrite, `{ ok, data, error, request_id }`
  envelope unwrap, typed `AbunderaApiException`, and the full endpoint surface
  (envelopes, templates, account-level webhooks, public verification).
- Reliability: retry on connection errors, `409`, `429`, and `5xx` (never other
  `4xx`); `Retry-After` honored; full-jitter exponential backoff capped at 20s;
  stable, auto-generated `Idempotency-Key` on every internally-retried `POST`,
  caller-overridable; independent connect and request timeouts; connection
  reuse via `java.net.http.HttpClient`.
- `Webhook`: Standard Webhooks signature verification — `v1` HMAC-SHA256 and
  `v1a` Ed25519, constant-time comparison, `whsec_`/`whpk_` prefix stripping,
  ±300s timestamp tolerance (injectable clock), space-delimited token rotation.
- Typed models (Java records) for envelopes, templates, webhooks, audit
  trails, and verification results.
- JUnit 5 test suite: webhook verifier unit tests (valid/tampered/wrong-key/
  skew/rotation, both schemes), client tests against an injected transport
  (retry, backoff jitter, idempotency, error mapping, `/api` rewrite), and the
  shared cross-language fixture contract test.
- Zero third-party HTTP dependency (`java.net.http.HttpClient`); only runtime
  dependency is `jackson-databind`.
