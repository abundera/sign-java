package ai.abundera.sign.model;

/** {@code PATCH /api/v1/envelopes/{id}}. */
public record EnvelopeUpdateResult(String id, boolean allowNegotiation) {
}
