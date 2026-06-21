package io.kelta.ai.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiMaskingService")
class PiiMaskingServiceTest {

    private final PiiMaskingService masker = new PiiMaskingService();

    @Test
    @DisplayName("redacts email addresses")
    void masksEmail() {
        assertThat(masker.mask("contact jane.doe@example.com please"))
                .isEqualTo("contact [REDACTED_EMAIL] please");
    }

    @Test
    @DisplayName("redacts US SSNs")
    void masksSsn() {
        assertThat(masker.mask("ssn 123-45-6789")).isEqualTo("ssn [REDACTED_SSN]");
    }

    @Test
    @DisplayName("redacts 16-digit card numbers (grouped or not)")
    void masksCreditCard() {
        assertThat(masker.mask("card 4111 1111 1111 1111")).contains("[REDACTED_CC]")
                .doesNotContain("4111");
        assertThat(masker.mask("card 4111111111111111")).contains("[REDACTED_CC]");
    }

    @Test
    @DisplayName("redacts NANP phone numbers")
    void masksPhone() {
        assertThat(masker.mask("call (555) 123-4567")).contains("[REDACTED_PHONE]")
                .doesNotContain("123-4567");
    }

    @Test
    @DisplayName("masks multiple PII items in one string")
    void masksMultiple() {
        String out = masker.mask("a@b.com and 123-45-6789");
        assertThat(out).contains("[REDACTED_EMAIL]").contains("[REDACTED_SSN]");
    }

    @Test
    @DisplayName("leaves non-PII text and null/blank untouched")
    void leavesCleanTextAlone() {
        assertThat(masker.mask("the quick brown fox")).isEqualTo("the quick brown fox");
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask("  ")).isEqualTo("  ");
    }
}
