package ai.abundera.sign.model;

import java.util.Map;

/** One row of {@code GET /api/v1/envelopes}. */
public record EnvelopeListItem(
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
        String source) {
}
