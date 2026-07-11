package ai.abundera.sign.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Body accepted by {@code POST /api/v1/envelopes}. Built via {@link #builder()};
 * unset optional fields are omitted from the serialized JSON entirely (never
 * sent as {@code null}), matching the Ruby/TypeScript reference SDKs' "unless
 * nil" / "optional?" behavior.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EnvelopeCreateParams {

    private final String templateId;
    private final List<SignerCreate> signers;
    private final Map<String, Object> fields;
    private final String callbackUrl;
    private final List<CcRecipient> cc;
    private final String message;
    private final String lang;
    private final String orgId;
    private final Boolean embedded;
    private final Boolean testMode;
    private final Boolean inPerson;
    private final Boolean signOrder;
    private final String accessCode;
    private final Boolean requireIdVerification;

    private EnvelopeCreateParams(Builder b) {
        this.templateId = b.templateId;
        this.signers = b.signers;
        this.fields = b.fields;
        this.callbackUrl = b.callbackUrl;
        this.cc = b.cc;
        this.message = b.message;
        this.lang = b.lang;
        this.orgId = b.orgId;
        this.embedded = b.embedded;
        this.testMode = b.testMode;
        this.inPerson = b.inPerson;
        this.signOrder = b.signOrder;
        this.accessCode = b.accessCode;
        this.requireIdVerification = b.requireIdVerification;
    }

    public String getTemplateId() {
        return templateId;
    }

    public List<SignerCreate> getSigners() {
        return signers;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public List<CcRecipient> getCc() {
        return cc;
    }

    public String getMessage() {
        return message;
    }

    public String getLang() {
        return lang;
    }

    public String getOrgId() {
        return orgId;
    }

    public Boolean getEmbedded() {
        return embedded;
    }

    public Boolean getTestMode() {
        return testMode;
    }

    public Boolean getInPerson() {
        return inPerson;
    }

    public Boolean getSignOrder() {
        return signOrder;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public Boolean getRequireIdVerification() {
        return requireIdVerification;
    }

    public static Builder builder(String templateId) {
        return new Builder(templateId);
    }

    /** Fluent builder. {@code templateId} is the only required field. */
    public static final class Builder {
        private final String templateId;
        private List<SignerCreate> signers;
        private Map<String, Object> fields;
        private String callbackUrl;
        private List<CcRecipient> cc;
        private String message;
        private String lang;
        private String orgId;
        private Boolean embedded;
        private Boolean testMode;
        private Boolean inPerson;
        private Boolean signOrder;
        private String accessCode;
        private Boolean requireIdVerification;

        private Builder(String templateId) {
            this.templateId = templateId;
        }

        public Builder signers(List<SignerCreate> signers) {
            this.signers = signers;
            return this;
        }

        /** Pre-filled field values keyed by field name. */
        public Builder fields(Map<String, Object> fields) {
            this.fields = fields;
            return this;
        }

        /** HTTPS URL that receives signed webhook callbacks for this envelope. */
        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public Builder cc(List<CcRecipient> cc) {
            this.cc = cc;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /** Signing-page language (26 supported, e.g. "en", "es", "ja"). */
        public Builder lang(String lang) {
            this.lang = lang;
            return this;
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder embedded(boolean embedded) {
            this.embedded = embedded;
            return this;
        }

        public Builder testMode(boolean testMode) {
            this.testMode = testMode;
            return this;
        }

        public Builder inPerson(boolean inPerson) {
            this.inPerson = inPerson;
            return this;
        }

        /** Enforce sequential signing order. */
        public Builder signOrder(boolean signOrder) {
            this.signOrder = signOrder;
            return this;
        }

        public Builder accessCode(String accessCode) {
            this.accessCode = accessCode;
            return this;
        }

        public Builder requireIdVerification(boolean requireIdVerification) {
            this.requireIdVerification = requireIdVerification;
            return this;
        }

        public EnvelopeCreateParams build() {
            if (templateId == null || templateId.isEmpty()) {
                throw new IllegalArgumentException("templateId is required");
            }
            return new EnvelopeCreateParams(this);
        }
    }
}
