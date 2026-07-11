package ai.abundera.sign.model;

/** {@code POST /api/v1/void-envelope}. */
public record VoidEnvelopeResult(String id, String status, Integer notifiedSigners) {
}
