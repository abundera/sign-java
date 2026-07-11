package ai.abundera.sign.model;

/** Carbon-copy recipient (receives the completed document, does not sign). */
public record CcRecipient(String email, String name) {

    public CcRecipient(String email) {
        this(email, null);
    }
}
