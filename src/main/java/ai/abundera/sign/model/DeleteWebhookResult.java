package ai.abundera.sign.model;

/** {@code DELETE /api/v1/webhooks/manage}. */
public record DeleteWebhookResult(String id, boolean deleted) {
}
