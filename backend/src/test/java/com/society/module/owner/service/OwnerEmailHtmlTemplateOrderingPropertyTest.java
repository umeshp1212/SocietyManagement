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
 * Property test for {@link OwnerEmailTemplateBuilder#buildHtmlBody}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 4: Template ordering is
 * header-then-body-then-footer.</b> For any valid {@link SocietySettings}, any
 * non-empty subject, and any {@code Sanitized_Body}, the assembled HTML document
 * places the Society Header before the Body and the Body before the Footer, i.e.
 * {@code index(header) < index(body) < index(footer)}.</p>
 *
 * <p><b>Validates: Requirements 2.2</b></p>
 */
class OwnerEmailHtmlTemplateOrderingPropertyTest {

    /**
     * Distinct marker embedded inside the sanitized body so its position can be located
     * unambiguously in the assembled HTML. It is valid allowed-formatting markup and
     * uses a token that cannot collide with header/footer labels or generated field values.
     */
    private static final String BODY_MARKER = "OWNEREMAILBODYMARKER";
    private static final String SANITIZED_BODY = "<p>" + BODY_MARKER + "</p>";

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    // Feature: owner-email-rich-text, Property 4: Template ordering is header-then-body-then-footer
    @Property(tries = 100)
    void htmlTemplateOrderingIsHeaderThenBodyThenFooter(
            @ForAll("validSettings") SocietySettings settings,
            @ForAll("nonEmptyText") String subject) {

        String html = templateBuilder.buildHtmlBody(settings, subject, SANITIZED_BODY);

        // Distinctive anchors, one per section, that cannot collide with random field values:
        //   Header anchor: the "Reg. No: " line built from the registration number.
        //   Body anchor:   the sanitized-body marker inserted verbatim.
        //   Footer anchor: the literal "Regards," salutation in the footer block.
        int headerAnchor = html.indexOf("Reg. No: " + settings.getRegistrationNumber().trim());
        int bodyAnchor = html.indexOf(BODY_MARKER);
        int footerAnchor = html.indexOf("Regards,");

        // Every section anchor must be present.
        assertThat(headerAnchor).as("header anchor present").isGreaterThanOrEqualTo(0);
        assertThat(bodyAnchor).as("body anchor present").isGreaterThanOrEqualTo(0);
        assertThat(footerAnchor).as("footer anchor present").isGreaterThanOrEqualTo(0);

        // Header precedes Body precedes Footer (Req 2.2).
        assertThat(headerAnchor)
                .as("header precedes body")
                .isLessThan(bodyAnchor);
        assertThat(bodyAnchor)
                .as("body precedes footer")
                .isLessThan(footerAnchor);
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
     * Non-empty subject: alphanumeric tokens with a leading non-space char so the
     * builder's {@code StringUtils.hasText} check passes and trimming keeps content.
     */
    @Provide
    Arbitrary<String> nonEmptyText() {
        return Arbitraries.strings().alpha().numeric().withChars(' ', '.')
                .ofMinLength(1).ofMaxLength(120)
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    /**
     * Non-blank settings token: alphanumeric so it never collides with the section
     * label prefixes ("Reg. No: ", "Regards,") nor with the body marker.
     */
    private Arbitrary<String> nonBlankToken() {
        return Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(40);
    }
}
