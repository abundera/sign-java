package ai.abundera.sign.model;

import java.util.List;

/** {@code GET /api/v1/envelopes}. */
public record EnvelopeList(List<EnvelopeListItem> envelopes, Pagination pagination) {
}
