package com.society.module.owner.service;

import com.society.enums.OwnerStatus;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.dto.SendReportDTO;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property test for {@link OwnerEmailServiceImpl#sendOwnerEmail}.
 *
 * <p><b>Feature: owner-email, Property 11: Recipient scope resolution matches the requested
 * scope.</b> For any request, resolving the {@link RecipientScope#SELECTED} scope shall yield
 * exactly the owners whose IDs were provided (via {@code OwnerRepository.findAllById}), and
 * resolving the {@link RecipientScope#ALL} scope shall yield exactly the active-owner set
 * (via the active-owner query {@code findByStatusOrderByFullNameAsc(ACTIVE)}); the request's
 * effective recipient set — evidenced by exactly which owners the mail transport is invoked
 * for — shall contain exactly those resolved owners.</p>
 *
 * <p>All generated owners carry a valid (non-blank) email so that every resolved recipient is
 * a {@code Valid_Recipient} and therefore dispatched, letting the set of dispatched addresses
 * stand in for the resolved recipient set. This isolates the scope-resolution concern from the
 * missing-email / failure classification concerns covered by the other properties.</p>
 *
 * <p><b>Validates: Requirements 2.3, 2.4</b></p>
 */
class OwnerEmailRecipientScopePropertyTest {

    // Feature: owner-email, Property 11: Recipient scope resolution matches the requested scope
    @Property(tries = 100)
    void selectedScopeResolvesExactlyTheProvidedIds(
            @ForAll("selectedScopeCases") Tuple.Tuple2<List<Owner>, List<Long>> testCase) throws Exception {

        List<Owner> activeOwners = testCase.get1();
        List<Long> selectedIds = testCase.get2();

        // Owners the repository returns for the provided ids (the SELECTED resolution result).
        List<Owner> selectedOwners = activeOwners.stream()
                .filter(o -> selectedIds.contains(o.getOwnerId()))
                .collect(Collectors.toList());

        // --- Arrange collaborators ---
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        // SELECTED scope resolves owners by id via findAllById(...).
        when(ownerRepository.findAllById(eq(selectedIds))).thenReturn(selectedOwners);
        // Guard: the ALL-scope active-owner query must NOT be consulted for SELECTED scope.
        when(ownerRepository.findByStatusOrderByFullNameAsc(any())).thenReturn(activeOwners);

        OwnerEmailServiceImpl service = newService(ownerRepository, mailSender);

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.SELECTED);
        request.setOwnerIds(selectedIds);
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: SELECTED resolves exactly the provided IDs (Req 2.3) ---
        // An empty id list resolves to zero recipients without a repository lookup; a non-empty
        // list is resolved by id via findAllById. Either way, the active-owner query is never used.
        if (selectedIds.isEmpty()) {
            verify(ownerRepository, never()).findAllById(any());
        } else {
            verify(ownerRepository).findAllById(eq(selectedIds));
        }
        verify(ownerRepository, never()).findByStatusOrderByFullNameAsc(any());

        assertThat(report.getTotalAttempted())
                .as("SELECTED scope attempts exactly the resolved-by-id recipients")
                .isEqualTo(selectedOwners.size());

        assertThat(dispatchedRecipients(mailSender, selectedOwners.size()))
                .as("mail transport is invoked for exactly the selected owners' emails")
                .containsExactlyInAnyOrderElementsOf(expectedEmails(selectedOwners));
    }

    // Feature: owner-email, Property 11: Recipient scope resolution matches the requested scope
    @Property(tries = 100)
    void allScopeResolvesExactlyTheActiveOwnerSet(
            @ForAll("activeOwnerSets") List<Owner> activeOwners) throws Exception {

        // --- Arrange collaborators ---
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        OwnerRepository ownerRepository = mock(OwnerRepository.class);
        // ALL scope resolves the active-owner set via the active-owner query.
        when(ownerRepository.findByStatusOrderByFullNameAsc(eq(OwnerStatus.ACTIVE)))
                .thenReturn(activeOwners);

        OwnerEmailServiceImpl service = newService(ownerRepository, mailSender);

        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        // ownerIds are irrelevant for ALL scope and must be ignored.
        request.setOwnerIds(List.of(999_999L));
        request.setSubject("Hello owners");
        request.setBody("This is the message body");

        // --- Act ---
        SendReportDTO report = service.sendOwnerEmail(request);

        // --- Assert: ALL resolves exactly the active-owner set (Req 2.4) ---
        verify(ownerRepository).findByStatusOrderByFullNameAsc(eq(OwnerStatus.ACTIVE));
        verify(ownerRepository, never()).findAllById(any());

        assertThat(report.getTotalAttempted())
                .as("ALL scope attempts exactly the active-owner set")
                .isEqualTo(activeOwners.size());

        assertThat(dispatchedRecipients(mailSender, activeOwners.size()))
                .as("mail transport is invoked for exactly the active owners' emails")
                .containsExactlyInAnyOrderElementsOf(expectedEmails(activeOwners));
    }

    private OwnerEmailServiceImpl newService(OwnerRepository ownerRepository,
                                             JavaMailSenderImpl mailSender) {
        SocietySettingsService settingsService = mock(SocietySettingsService.class);
        when(settingsService.getSettings()).thenReturn(new SocietySettings());

        // Stub the pure template builder so template concerns do not affect this property.
        OwnerEmailTemplateBuilder templateBuilder = mock(OwnerEmailTemplateBuilder.class);
        when(templateBuilder.buildSubject(any())).thenReturn("Subject");
        when(templateBuilder.buildHtmlBody(any(), any(), any())).thenReturn("<p>Body</p>");

        // The sanitizer and plain-text renderer are pure components; the real ones pass the
        // plain-text body through unchanged so the send flow behaves exactly as before.
        OwnerEmailServiceImpl service =
                new OwnerEmailServiceImpl(ownerRepository, settingsService, templateBuilder,
                        new OwnerEmailSanitizer(), new OwnerEmailPlainTextRenderer());
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@society.com");
        return service;
    }

    /** The email addresses expected to be dispatched for the given resolved owners. */
    private List<String> expectedEmails(List<Owner> resolved) {
        return resolved.stream()
                .map(o -> o.getEmail().trim())
                .collect(Collectors.toList());
    }

    /** Captures the single recipient of each dispatched message (all owners have valid emails). */
    private List<String> dispatchedRecipients(JavaMailSenderImpl mailSender, int expectedSends)
            throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.times(expectedSends)).send(captor.capture());
        List<String> recipients = new ArrayList<>();
        for (MimeMessage sent : captor.getAllValues()) {
            recipients.add(sent.getRecipients(Message.RecipientType.TO)[0].toString());
        }
        return recipients;
    }

    /**
     * Generates a SELECTED-scope case: an active-owner set (each with a unique id and a valid,
     * unique email) paired with a subset of those ids to select. The subset is a genuine subset
     * of the available ids so {@code findAllById} resolves exactly the selected owners.
     */
    @Provide
    Arbitrary<Tuple.Tuple2<List<Owner>, List<Long>>> selectedScopeCases() {
        return validOwnerSet(1, 30).flatMap(owners -> {
            List<Long> allIds = owners.stream().map(Owner::getOwnerId).collect(Collectors.toList());
            // Choose a subset of the available ids (may be empty or the full set).
            return Arbitraries.subsetOf(allIds)
                    .map(subset -> Tuple.of(owners, new ArrayList<>(subset)));
        });
    }

    /** Generates an active-owner set (0..30 owners) with unique ids and valid, unique emails. */
    @Provide
    Arbitrary<List<Owner>> activeOwnerSets() {
        return validOwnerSet(0, 30);
    }

    /**
     * Builds owners with unique ids and valid, unique, non-blank emails so that every resolved
     * recipient is dispatched (isolating scope resolution from email-validity classification).
     * Uniqueness of emails lets the dispatched-address multiset equal the resolved-owner set.
     */
    private Arbitrary<List<Owner>> validOwnerSet(int minSize, int maxSize) {
        return owner().list().ofMinSize(minSize).ofMaxSize(maxSize)
                .uniqueElements(Owner::getOwnerId)
                .uniqueElements(Owner::getEmail);
    }

    private Arbitrary<Owner> owner() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 1_000_000L);
        Arbitrary<String> names = Arbitraries.strings().alpha().numeric()
                .ofMinLength(1).ofMaxLength(30);
        // Always a valid, non-blank email so every resolved recipient is a Valid_Recipient.
        Arbitrary<String> emails = Arbitraries.strings().alpha().numeric()
                .ofMinLength(5).ofMaxLength(20)
                .map(local -> local + "@example.com");

        return Combinators.combine(ids, names, emails).as((id, name, email) ->
                Owner.builder()
                        .ownerId(id)
                        .fullName(name)
                        .email(email)
                        .build());
    }
}
