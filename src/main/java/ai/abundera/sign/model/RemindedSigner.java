package ai.abundera.sign.model;

/** One entry of {@link RemindResult#reminded()}. */
public record RemindedSigner(String id, String email, String name) {
}
