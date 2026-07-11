package ai.abundera.sign.model;

import java.util.List;

/** {@code GET /api/v1/webhooks/manage/deliveries}. */
public record WebhookDeliveryList(List<WebhookDelivery> deliveries, Pagination pagination) {
}
