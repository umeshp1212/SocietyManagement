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
 * <p><b>Feature: owner-email, Property 1: Template ordering is always
 * header-then-body-then-footer.</b> For any valid {@link SocietySettings} and any
 * non-empty subject and message, the assembled email content places the Society
 * Header before the Body and the Body before the Footer.</p>
 *
 * <p><b>Validates: Requirements 4.1</b></p>
 */
class OwnerEmailTemplateOrderingPropertyTest {

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    // Feature: owner-email, Property 1: Template ordering is always header-then-body-then-footer
    @Property(tries = 100)
    void templateOrderingIsHeaderThenBodyThenFooter(
            @ForAll("validSettings") SocietySettings settings,
            @ForAll("nonEmptyText") String subject,
            @ForAll("nonEmptyText") String message) {

        String content = templateBuilder.buildBody(settings, subject, message);

        // Use distinctive, prefixed anchors that cannot collide with random generated
        // field values: each section carries a literal label unique to that section.
        //   Header anchor: the "Reg. No: " line built from the registration number.
        //   Body anchor:   the "Subject: " line built from the user subject.
        //   Footer anchor: the literal "Regards," salutation.
        int headerAnchor = content.indexOf("Reg. No: " + settings.getRegistrationNumber().trim());
        int bodyAnchor = content.indexOf("Subject: " + subject.trim());
        int footerAnchor = content.indexOf("Regards,");

        // Every section anchor must be present.
        assertThat(headerAnchor).as("header anchor present").isGreaterThanOrEqualTo(0);
        assertThat(bodyAnchor).as("body anchor present").isGreaterThanOrEqualTo(0);
        assertThat(footerAnchor).as("footer anchor present").isGreaterThanOrEqualTo(0);

        // Header precedes Body precedes Footer (Req 4.1).
        assertThat(headerAnchor)
                .as("header precedes body")
                .isLessThan(bodyAnchor);
        assertThat(bodyAnchor)
                .as("body precedes footer")
                .isLessThan(footerAnchor);

        // The user message content is part of the Body: it must appear after the body
        // anchor and before the footer.
        int messageIndex = content.indexOf(message.trim(), bodyAnchor);
        assertThat(messageIndex).as("message present within body").isGreaterThanOrEqualTo(bodyAnchor);
        assertThat(messageIndex).as("message precedes footer").isLessThan(footerAnchor);
    }

    /**
     * Generates {@link SocietySettings} with all header and footer required fields non-blank,
     * so assembly is never rejected and the ordering invariant is exercised on valid input.
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
