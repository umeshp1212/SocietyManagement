package com.society.module.owner.service;

import com.society.module.settings.entity.SocietySettings;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link OwnerEmailTemplateBuilder#buildBody}.
 *
 * <p><b>Feature: owner-email, Property 4: Footer contains the signatory names.</b>
 * For any valid {@link SocietySettings}, the assembled Footer contains the chairman,
 * secretary, and treasurer names from those settings.</p>
 *
 * <p><b>Validates: Requirements 4.4</b></p>
 */
class OwnerEmailTemplateFooterPropertyTest {

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    // Feature: owner-email, Property 4: Footer contains the signatory names
    @Property(tries = 100)
    void footerContainsChairmanSecretaryAndTreasurerNames(
            @ForAll("validSettings") SocietySettings settings,
            @ForAll("nonEmptyText") String subject,
            @ForAll("nonEmptyText") String message) {

        String content = templateBuilder.buildBody(settings, subject, message);

        // The footer is the section that follows the Body. It begins at the literal
        // "Regards," salutation, so isolate the footer substring to assert the signatory
        // names are present within the footer rather than anywhere in the content.
        int footerAnchor = content.indexOf("Regards,");
        assertThat(footerAnchor).as("footer anchor present").isGreaterThanOrEqualTo(0);
        String footer = content.substring(footerAnchor);

        // Chairman name (Req 4.4).
        assertThat(footer)
                .as("footer contains chairman name")
                .contains(settings.getChairmanName().trim());

        // Secretary name (Req 4.4).
        assertThat(footer)
                .as("footer contains secretary name")
                .contains(settings.getSecretaryName().trim());

        // Treasurer name (Req 4.4).
        assertThat(footer)
                .as("footer contains treasurer name")
                .contains(settings.getTreasurerName().trim());
    }

    /**
     * Generates {@link SocietySettings} with all header and footer required fields non-blank,
     * so assembly is never rejected and the footer invariant is exercised on valid input.
     */
    @Provide
    Arbitrary<SocietySettings> validSettings() {
        Arbitrary<String> nonBlank = nonBlankToken();
        return Combinators.combine(
                        nonBlank, nonBlank, nonBlank, nonBlank, nonBlank, nonBlank)
                .as((societyName, addressLine1, city, registrationNumber, phone, email) ->
                        SocietySettings.builder()
                                .societyName(societyName)
                                .addressLine1(addressLine1)
                                .city(city)
                                .registrationNumber(registrationNumber)
                                .phone(phone)
                                .email(email)
                                .build())
                .flatMap(base -> Combinators.combine(nonBlank, nonBlank, nonBlank)
                        .as((chairman, secretary, treasurer) -> {
                            base.setChairmanName(chairman);
                            base.setSecretaryName(secretary);
                            base.setTreasurerName(treasurer);
                            return base;
                        }));
    }

    /**
     * Non-empty subject/message: alphanumeric tokens with a leading non-space char so the
     * builder's {@code StringUtils.hasText} check passes and trimming keeps content.
     */
    @Provide
    Arbitrary<String> nonEmptyText() {
        return Arbitraries.strings().alpha().numeric().withChars(' ', '\n', '.')
                .ofMinLength(1).ofMaxLength(120)
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    private Arbitrary<String> nonBlankToken() {
        return Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(40);
    }
}
