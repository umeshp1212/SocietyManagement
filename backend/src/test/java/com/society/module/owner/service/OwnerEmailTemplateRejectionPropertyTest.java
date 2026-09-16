package com.society.module.owner.service;

import com.society.exception.BusinessException;
import com.society.module.settings.entity.SocietySettings;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property test for {@link OwnerEmailTemplateBuilder#buildBody}.
 *
 * <p><b>Feature: owner-email, Property 5: Assembly rejects missing user or settings
 * fields.</b> For any assembly request where the user subject is empty/whitespace, the
 * message is empty/whitespace, or a required header/footer settings field is missing/blank,
 * the template builder rejects the assembly with a {@link BusinessException} whose message
 * identifies the offending field, and no email content is produced.</p>
 *
 * <p><b>Validates: Requirements 4.6, 4.7</b></p>
 */
class OwnerEmailTemplateRejectionPropertyTest {

    private final OwnerEmailTemplateBuilder templateBuilder = new OwnerEmailTemplateBuilder();

    /**
     * A single rejection scenario: an otherwise-valid assembly request in which exactly one
     * field (the user subject, the user message, or a required settings field) is made
     * missing/blank, paired with the message fragment the builder is expected to use to
     * identify that offending field.
     */
    static final class RejectionScenario {
        final SocietySettings settings;
        final String subject;
        final String message;
        final String expectedFieldFragment;
        final String description;

        RejectionScenario(SocietySettings settings, String subject, String message,
                          String expectedFieldFragment, String description) {
            this.settings = settings;
            this.subject = subject;
            this.message = message;
            this.expectedFieldFragment = expectedFieldFragment;
            this.description = description;
        }

        @Override
        public String toString() {
            return description + " (expects '" + expectedFieldFragment + "')";
        }
    }

    // Feature: owner-email, Property 5: Assembly rejects missing user or settings fields
    @Property(tries = 200)
    void assemblyRejectsMissingUserOrSettingsFields(@ForAll("rejectionScenarios") RejectionScenario scenario) {
        assertThatThrownBy(() ->
                templateBuilder.buildBody(scenario.settings, scenario.subject, scenario.message))
                .as("assembly rejected for %s", scenario.description)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(scenario.expectedFieldFragment);
    }

    /**
     * Builds rejection scenarios covering each way an assembly request can be invalid:
     * <ul>
     *   <li>(a) empty/whitespace subject with valid message and settings,</li>
     *   <li>(b) empty/whitespace message with valid subject and settings,</li>
     *   <li>(c) each required header/footer settings field individually missing/blank,
     *       with a valid subject and message.</li>
     * </ul>
     * Each scenario carries the message fragment that identifies the offending field so the
     * property can assert the {@link BusinessException} names the correct field.
     */
    @Provide
    Arbitrary<RejectionScenario> rejectionScenarios() {
        Arbitrary<RejectionScenario> subjectMissing = missingSubjectScenarios();
        Arbitrary<RejectionScenario> messageMissing = missingMessageScenarios();
        Arbitrary<RejectionScenario> settingsMissing = missingSettingsScenarios();
        return Arbitraries.oneOf(subjectMissing, messageMissing, settingsMissing);
    }

    // ----- (a) empty/whitespace subject -----

    private Arbitrary<RejectionScenario> missingSubjectScenarios() {
        return Combinators.combine(validSettings(), blankText(), nonEmptyText())
                .as((settings, blankSubject, message) ->
                        new RejectionScenario(settings, blankSubject, message,
                                "Subject is required", "empty/whitespace subject"));
    }

    // ----- (b) empty/whitespace message -----

    private Arbitrary<RejectionScenario> missingMessageScenarios() {
        return Combinators.combine(validSettings(), nonEmptyText(), blankText())
                .as((settings, subject, blankMessage) ->
                        new RejectionScenario(settings, subject, blankMessage,
                                "Message content is required", "empty/whitespace message"));
    }

    // ----- (c) each required settings field individually missing/blank -----

    private Arbitrary<RejectionScenario> missingSettingsScenarios() {
        // Each mutator blanks out exactly one required field; the paired fragment is the
        // message the builder uses to identify that offending settings field.
        Arbitrary<FieldMutation> mutations = Arbitraries.of(
                new FieldMutation("society name", "Society name is missing",
                        (s, blank) -> s.setSocietyName(blank)),
                new FieldMutation("address", "Society address is missing",
                        (s, blank) -> {
                            // Address is required as "at least one address part present"; blank them all.
                            s.setAddressLine1(blank);
                            s.setAddressLine2(blank);
                            s.setCity(blank);
                            s.setState(blank);
                            s.setPincode(blank);
                        }),
                new FieldMutation("registration number", "Society registration number is missing",
                        (s, blank) -> s.setRegistrationNumber(blank)),
                new FieldMutation("phone", "Society phone is missing",
                        (s, blank) -> s.setPhone(blank)),
                new FieldMutation("email", "Society email is missing",
                        (s, blank) -> s.setEmail(blank)),
                new FieldMutation("chairman name", "Chairman name is missing",
                        (s, blank) -> s.setChairmanName(blank)),
                new FieldMutation("secretary name", "Secretary name is missing",
                        (s, blank) -> s.setSecretaryName(blank)),
                new FieldMutation("treasurer name", "Treasurer name is missing",
                        (s, blank) -> s.setTreasurerName(blank)));

        return Combinators.combine(validSettings(), nonEmptyText(), nonEmptyText(),
                        mutations, blankOrNull())
                .as((settings, subject, message, mutation, blankValue) -> {
                    mutation.apply(settings, blankValue);
                    return new RejectionScenario(settings, subject, message,
                            mutation.expectedFragment, "missing settings field: " + mutation.fieldName);
                });
    }

    /** A named mutation that blanks a single required settings field. */
    private static final class FieldMutation {
        final String fieldName;
        final String expectedFragment;
        private final Mutator mutator;

        FieldMutation(String fieldName, String expectedFragment, Mutator mutator) {
            this.fieldName = fieldName;
            this.expectedFragment = expectedFragment;
            this.mutator = mutator;
        }

        void apply(SocietySettings settings, String blankValue) {
            mutator.mutate(settings, blankValue);
        }
    }

    @FunctionalInterface
    private interface Mutator {
        void mutate(SocietySettings settings, String blankValue);
    }

    // ----- Generators -----

    /**
     * Generates {@link SocietySettings} with all header and footer required fields non-blank,
     * so that only the field deliberately blanked by a scenario causes rejection.
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

    /**
     * Empty/whitespace text: either an empty string or a run of whitespace characters, so
     * {@code StringUtils.hasText} reports no text and the builder rejects it.
     */
    @Provide
    Arbitrary<String> blankText() {
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> whitespace = Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(1).ofMaxLength(8);
        return Arbitraries.oneOf(empty, whitespace);
    }

    /**
     * Missing/blank settings field value: {@code null}, empty, or whitespace-only. All three
     * cause {@code StringUtils.hasText} to report no text.
     */
    private Arbitrary<String> blankOrNull() {
        Arbitrary<String> nullValue = Arbitraries.just(null);
        return Arbitraries.oneOf(nullValue, blankText());
    }

    private Arbitrary<String> nonBlankToken() {
        return Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(40);
    }
}
