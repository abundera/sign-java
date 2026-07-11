package ai.abundera.sign.model;

/** One delivery attempt row. */
public record WebhookDelivery(
        String id,
        String eventId,
        String webhookId,
        String envelopeId,
        String event,
        String url,
        String status,
        int attempt,
        int maxAttempts,
        Integer responseCode,
        Integer durationMs,
        String error) {
}
