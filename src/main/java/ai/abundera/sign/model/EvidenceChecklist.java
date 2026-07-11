package ai.abundera.sign.model;

import java.util.Map;

/** {@code GET /api/v1/envelopes/{id}/evidence}. */
public record EvidenceChecklist(boolean complete, Map<String, Object> checklist) {
}
