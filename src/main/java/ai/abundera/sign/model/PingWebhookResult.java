package ai.abundera.sign.model;

/** {@code POST /api/v1/webhooks/manage/ping}. */
public record PingWebhookResult(boolean delivered, String webhookId, String eventId) {
}
