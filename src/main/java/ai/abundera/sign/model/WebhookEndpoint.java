package ai.abundera.sign.model;

import java.util.List;

/**
 * A registered account-level webhook endpoint. Named {@code WebhookEndpoint}
 * (not {@code Webhook}) to avoid colliding with {@link ai.abundera.sign.Webhook},
 * the signature verifier.
 */
public record WebhookEndpoint(String id, String url, String secret, List<String> events, boolean active) {
}
