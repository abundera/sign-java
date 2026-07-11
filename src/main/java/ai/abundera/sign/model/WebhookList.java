package ai.abundera.sign.model;

import java.util.List;

/** {@code GET /api/v1/webhooks/manage}. */
public record WebhookList(List<WebhookEndpoint> webhooks) {
}
