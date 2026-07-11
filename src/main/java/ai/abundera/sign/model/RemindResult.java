package ai.abundera.sign.model;

import java.util.List;

/** {@code POST /api/v1/remind}. */
public record RemindResult(String envelopeId, List<RemindedSigner> reminded, int count) {
}
