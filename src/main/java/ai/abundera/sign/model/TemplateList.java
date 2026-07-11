package ai.abundera.sign.model;

import java.util.List;

/** {@code GET /api/v1/templates}. */
public record TemplateList(int templateCount, List<TemplateSummary> templates, Pagination pagination) {
}
