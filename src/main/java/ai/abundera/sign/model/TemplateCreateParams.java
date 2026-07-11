package ai.abundera.sign.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Body for {@code POST /api/v1/templates} (create) and
 * {@code PUT /api/v1/templates/{id}} (full replace). Built via
 * {@link #builder(String, String)}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TemplateCreateParams {

    private final String name;
    private final String markdown;
    private final String description;
    private final String category;
    private final List<String> tags;
    private final Integer expiryDays;

    private TemplateCreateParams(Builder b) {
        this.name = b.name;
        this.markdown = b.markdown;
        this.description = b.description;
        this.category = b.category;
        this.tags = b.tags;
        this.expiryDays = b.expiryDays;
    }

    public String getName() {
        return name;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
    }

    public Integer getExpiryDays() {
        return expiryDays;
    }

    public static Builder builder(String name, String markdown) {
        return new Builder(name, markdown);
    }

    public static final class Builder {
        private final String name;
        private final String markdown;
        private String description;
        private String category;
        private List<String> tags;
        private Integer expiryDays;

        private Builder(String name, String markdown) {
            this.name = name;
            this.markdown = markdown;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder expiryDays(int expiryDays) {
            this.expiryDays = expiryDays;
            return this;
        }

        public TemplateCreateParams build() {
            return new TemplateCreateParams(this);
        }
    }
}
