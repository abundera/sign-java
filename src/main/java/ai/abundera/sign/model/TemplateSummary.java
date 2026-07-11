package ai.abundera.sign.model;

import java.util.List;

/** One row of {@code GET /api/v1/templates}. */
public record TemplateSummary(
        String id,
        String name,
        String description,
        Integer version,
        String category,
        List<String> tags,
        List<String> roles,
        Integer signerCount,
        Integer expiryDays,
        String createdAt,
        String updatedAt,
        String orgId,
        String createdBy,
        String templateType) {
}
