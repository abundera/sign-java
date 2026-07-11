package ai.abundera.sign.model;

import java.util.List;

/**
 * {@code GET /api/v1/templates/{id}} — {@link TemplateSummary} plus the
 * fields only the single-resource endpoint returns. Flattened rather than
 * inherited, mirroring the wire shape of the TypeScript
 * {@code Template extends TemplateSummary} interface.
 */
public record Template(
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
        String templateType,
        String disclaimer,
        String templateMarkdown,
        Integer hidden) {
}
