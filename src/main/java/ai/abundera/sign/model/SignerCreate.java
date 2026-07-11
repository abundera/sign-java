package ai.abundera.sign.model;

/** Signer as supplied when creating an envelope. */
public record SignerCreate(String email, String name, String phone) {

    public SignerCreate(String email, String name) {
        this(email, name, null);
    }
}
