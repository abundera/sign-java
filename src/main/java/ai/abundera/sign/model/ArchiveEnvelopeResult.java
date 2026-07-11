package ai.abundera.sign.model;

/** {@code POST /api/v1/archive-envelope}. */
public record ArchiveEnvelopeResult(String id, String status, String archivedAt) {
}
