package ai.abundera.sign.model;

import java.util.Map;

/**
 * {@code GET /api/v1/envelopes/{id}} — {@link EnvelopeListItem} plus the
 * fields only the single-resource endpoint returns. Flattened rather than
 * inherited (Java records cannot extend one another), matching how the
 * TypeScript {@code Envelope extends EnvelopeListItem} interface serializes
 * on the wire.
 */
public record Envelope(
        String id,
        String templateId,
        String status,
        String createdAt,
        String expiresAt,
        String completedAt,
        Integer retentionYears,
        Integer reminderDays,
        Integer maxReminders,
        Map<String, Object> metadata,
        String senderEmail,
        String senderName,
        String archivedAt,
        String source,
        String templateName,
        Boolean hasAccessCode,
        Boolean signOrder) {
}
