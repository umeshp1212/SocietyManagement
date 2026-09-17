package com.society.module.owner.service;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link OwnerEmailPlainTextRenderer#render(String, String, com.society.module.settings.entity.SocietySettings)}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 6: Plain-text alternative derives from visible text.</b>
 * For any {@code Sanitized_Body} composed of {@code Allowed_Formatting} elements — emphasis
 * ({@code b}/{@code strong}/{@code i}/{@code em}/{@code u}/{@code s}/{@code strike}), headings
 * ({@code h1}-{@code h6}), paragraphs ({@code p}), line breaks ({@code br}), ordered/unordered
 * lists ({@code ol}/{@code ul} with {@code li}), and allowed-scheme hyperlinks ({@code a}) — the
 * rendered {@code Plain_Text_Alternative}:
 * <ul>
 *   <li>contains no HTML tag markup (no {@code <tag ...>} sequences leak into the plain text), and</li>
 *   <li>includes the visible textual content of the sanitized body (every visible word is present).</li>
 * </ul>
 * The renderer derives the plain text solely by walking the sanitized DOM, so the visible text
 * of the body must survive into the plain-text alternative.</p>
 *
 * <p><b>Validates: Requirements 7.2</b></p>
 */
class OwnerEmailPlainTextDerivationPropertyTest {

    private final OwnerEmailPlainTextRenderer renderer = new OwnerEmailPlainTextRenderer();
    private final OwnerEmailSanitizer sanitizer = new OwnerEmailSanitizer();

    // Feature: owner-email-rich-text, Property 6: Plain-text alternative derives from visible text
    @Property(tries = 100)
    void plainTextContainsNoMarkupAndIncludesVisibleText(
            @ForAll("sanitizedBody") BodyDocument document,
            @ForAll("subject") String subject) {

        // The body is authored from the allow-list, then passed through the authoritative
        // sanitizer so we render exactly what the service would render (a Sanitized_Body).
        String sanitizedBody = sanitizer.sanitize(document.html);

        String plainText = renderer.render(sanitizedBody, subject, null);

        // 1. No HTML tag markup leaks into the plain-text alternative. Any residual
        //    "<tag>" / "</tag>" / "<tag ...>" sequence would mean the DOM was not walked.
        assertThat(plainText)
                .as("plain text must not contain HTML tag markup for sanitized body <%s>", sanitizedBody)
                .doesNotContainPattern("</?[a-zA-Z][^>]*>");

        // 2. The visible textual content of the sanitized body is present in the plain text.
        //    Compare on the visible words (the sanitizer's own visible-text extraction), so the
        //    check is independent of block/line-break formatting introduced by the renderer.
        String visibleText = sanitizer.visibleText(sanitizedBody);
        for (String word : visibleWords(visibleText)) {
            assertThat(plainText)
                    .as("plain text must include visible word \"%s\" from sanitized body <%s>",
                            word, sanitizedBody)
                    .contains(word);
        }
    }

    /** Splits visible text into non-empty whitespace-delimited words. */
    private static List<String> visibleWords(String visibleText) {
        List<String> words = new ArrayList<>();
        if (visibleText == null || visibleText.isBlank()) {
            return words;
        }
        for (String token : visibleText.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                words.add(token);
            }
        }
        return words;
    }

    /** A generated allowed-formatting document. */
    private static final class BodyDocument {
        final String html;

        BodyDocument(String html) {
            this.html = html;
        }
    }

    /** Subject line, kept simple and markup-free. */
    @Provide
    Arbitrary<String> subject() {
        return safeText();
    }

    /**
     * Generates {@code Sanitized_Body} candidates composed only of allowed-formatting elements:
     * emphasis, headings, paragraphs, line breaks, ordered/unordered lists, and allowed-scheme
     * hyperlinks.
     */
    @Provide
    Arbitrary<BodyDocument> sanitizedBody() {
        return allowedFragment().list().ofMinSize(1).ofMaxSize(8)
                .map(fragments -> {
                    StringBuilder html = new StringBuilder();
                    for (String f : fragments) {
                        html.append(f);
                    }
                    return new BodyDocument(html.toString());
                });
    }

    /** Random markup drawn exclusively from the allowed-formatting allow-list. */
    private Arbitrary<String> allowedFragment() {
        Arbitrary<String> text = safeText();
        return Arbitraries.oneOf(
                text.map(t -> "<b>" + t + "</b>"),
                text.map(t -> "<strong>" + t + "</strong>"),
                text.map(t -> "<i>" + t + "</i>"),
                text.map(t -> "<em>" + t + "</em>"),
                text.map(t -> "<u>" + t + "</u>"),
                text.map(t -> "<s>" + t + "</s>"),
                text.map(t -> "<strike>" + t + "</strike>"),
                text.map(t -> "<h1>" + t + "</h1>"),
                text.map(t -> "<h2>" + t + "</h2>"),
                text.map(t -> "<h3>" + t + "</h3>"),
                text.map(t -> "<h4>" + t + "</h4>"),
                text.map(t -> "<h5>" + t + "</h5>"),
                text.map(t -> "<h6>" + t + "</h6>"),
                text.map(t -> "<p>" + t + "</p>"),
                text.map(t -> t + "<br>"),
                Combinators.combine(text, text).as((a, b) ->
                        "<ul><li>" + a + "</li><li>" + b + "</li></ul>"),
                Combinators.combine(text, text).as((a, b) ->
                        "<ol><li>" + a + "</li><li>" + b + "</li></ol>"),
                Combinators.combine(allowedHref(), text).as((href, t) ->
                        "<a href=\"" + href + "\">" + t + "</a>"));
    }

    /** Hyperlink targets whose scheme is on the allow-list (http/https/mailto). */
    private Arbitrary<String> allowedHref() {
        return Arbitraries.of(
                "http://example.com/path",
                "https://example.com/secure",
                "mailto:owner@example.com");
    }

    /**
     * Plain text with a single word per fragment (no spaces), never containing angle brackets,
     * ampersands, or quotes so it can never introduce markup, and never empty so no element
     * collapses to nothing. Keeping words space-free makes the visible-word containment check
     * exact regardless of how the renderer joins adjacent inline text.
     */
    private Arbitrary<String> safeText() {
        return Arbitraries.strings().alpha().numeric().withChars('.', ',')
                .ofMinLength(1).ofMaxLength(24);
    }
}
