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
 * Property test for {@link OwnerEmailTemplateBuilder#buildBody} and
 * {@link OwnerEmailTemplateBuilder#buildSubject}.
 *
 * <p><b>Feature: owner-email, Property 3: Body contains the user subject and message.</b>
 * For any valid {@link SocietySettings} and any non-empty subject and message, the
 * assembled email content contains both the user-entered subject and the user-entered
 * message, and {@code buildSubject} returns exactly the user-entered subject.</p>
 *
 * <p><b>Validates: Requirements 4.3, 4.5</b></p>
 */
class OwnerEmailTemplateBodyPropertyTest {

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    // Feature: owner-email, Property 3: Body contains the user subject and message
    @Property(tries = 100)
    void bodyContainsUserSubjectAndMessageAndSubjectLineEqualsUserSubject(
            @ForAll("validSettings") SocietySettings settings,
            @ForAll("nonEmptyText") String subject,
            @ForAll("nonEmptyText") String message) {

        String content = templateBuilder.buildBody(settings, subject, message);

        // Req 4.3: the assembled body carries both the user-entered subject and message.
        assertThat(content)
                .as("assembled body contains the user-entered subject")
                .contains(subject.trim());
        assertThat(content)
                .as("assembled body contains the user-entered message")
                .contains(message.trim());

        // The user message belongs to the Body section: it appears after the "Subject: "
        // anchor and before the "Regards," footer salutation.
        int bodyAnchor = content.indexOf("Subject: " + subject.trim());
        int footerAnchor = content.indexOf("Regards,");
        assertThat(bodyAnchor).as("body anchor present").isGreaterThanOrEqualTo(0);
        assertThat(footerAnchor).as("footer anchor present").isGreaterThanOrEqualTo(0);

        int messageIndex = content.indexOf(message.trim(), bodyAnchor);
        assertThat(messageIndex)
                .as("message appears within the body section")
                .isGreaterThanOrEqualTo(bodyAnchor);
        assertThat(messageIndex)
                .as("message precedes the footer")
                .isLessThan(footerAnchor);

        // Req 4.5: the email subject line equals the user-entered subject.
        assertThat(templateBuilder.buildSubject(subject))
                .as("subject line equals the user-entered subject")
                .isEqualTo(subject.trim());
    }

    /**
     * Generates {@link SocietySettings} with all header and footer required fields non-blank,
     * so assembly is never rejected and the body invariant is exercised on valid input.
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
