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
 * <p><b>Feature: owner-email, Property 2: Header contains all required society identity
 * fields.</b> For any valid {@link SocietySettings}, the assembled Society Header contains
 * the society name, address, registration number, phone, and email values from those
 * settings.</p>
 *
 * <p><b>Validates: Requirements 4.2</b></p>
 */
class OwnerEmailTemplateHeaderPropertyTest {

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    // Feature: owner-email, Property 2: Header contains all required society identity fields
    @Property(tries = 100)
    void headerContainsAllRequiredSocietyIdentityFields(
            @ForAll("validSettings") SocietySettings settings,
            @ForAll("nonEmptyText") String subject,
            @ForAll("nonEmptyText") String message) {

        String content = templateBuilder.buildBody(settings, subject, message);

        // The header is the section that precedes the Body. The Body begins at the
        // "Subject: " anchor, so isolate the header substring to assert identity fields
        // are present within the header rather than anywhere in the content.
        int bodyAnchor = content.indexOf("Subject: " + subject.trim());
        assertThat(bodyAnchor).as("body anchor present").isGreaterThanOrEqualTo(0);
        String header = content.substring(0, bodyAnchor);

        // Society name (Req 4.2).
        assertThat(header)
                .as("header contains society name")
                .contains(settings.getSocietyName().trim());

        // Address (Req 4.2): each populated address part appears in the header.
        assertThat(header)
                .as("header contains address line 1")
                .contains(settings.getAddressLine1().trim());
        assertThat(header)
                .as("header contains city")
                .contains(settings.getCity().trim());

        // Registration number (Req 4.2).
        assertThat(header)
                .as("header contains registration number")
                .contains(settings.getRegistrationNumber().trim());

        // Phone (Req 4.2).
        assertThat(header)
                .as("header contains phone")
                .contains(settings.getPhone().trim());

        // Email (Req 4.2).
        assertThat(header)
                .as("header contains email")
                .contains(settings.getEmail().trim());
    }

    /**
     * Generates {@link SocietySettings} with all header and footer required fields non-blank,
     * so assembly is never rejected and the header invariant is exercised on valid input.
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
