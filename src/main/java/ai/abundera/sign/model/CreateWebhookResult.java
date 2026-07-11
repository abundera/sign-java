package ai.abundera.sign.model;

/**
 * {@code POST /api/v1/webhooks/manage}. The returned {@code secret}
 * ({@code whsec_...}) is shown in full only once — store it.
 */
public record CreateWebhookResult(WebhookEndpoint webhook) {
}
