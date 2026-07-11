package ai.abundera.sign.model;

/** The {@code envelope} block of a {@link VerifyResult}. */
public record VerifyEnvelopeSummary(
        String id,
        String status,
        String templateId,
        String senderName,
        String senderEmail,
        String createdAt,
        String completedAt,
        Integer retentionYears) {
}
