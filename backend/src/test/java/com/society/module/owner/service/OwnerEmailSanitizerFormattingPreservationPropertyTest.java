package com.society.module.owner.service;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link OwnerEmailSanitizer#sanitize(String)} formatting preservation.
 *
 * <p><b>Feature: owner-email-rich-text, Property 3: Allowed formatting is preserved.</b>
 * For any {@code Rich_Text_Body} composed only of {@code Allowed_Formatting} elements —
 * bold/italic/underline/strikethrough emphasis ({@code b}/{@code strong}/{@code i}/
 * {@code em}/{@code u}/{@code s}/{@code strike}), headings ({@code h1}-{@code h6}),
 * ordered and unordered lists ({@code ol}/{@code ul} with {@code li} items), paragraphs
 * ({@code p}), line breaks ({@code br}), and allowed-scheme
 * ({@code http}/{@code https}/{@code mailto}) hyperlinks ({@code a}) — every one of those
 * elements is still present in the {@code Sanitized_Body}. The allow-list is
 * tag-name-preserving, so {@code b} vs {@code strong}, {@code i} vs {@code em}, and
 * {@code s} vs {@code strike} each survive as authored.</p>
 *
 * <p><b>Validates: Requirements 2.3</b></p>
 */
class OwnerEmailSanitizerFormattingPreservationPropertyTest {

    private final OwnerEmailSanitizer sanitizer = new OwnerEmailSanitizer();

    // Feature: owner-email-rich-text, Property 3: Allowed formatting is preserved
    @Property(tries = 100)
    void everyAllowedFormattingElementSurvivesSanitization(
            @ForAll("allowedFormattingDocument") FormattingDocument document) {

        String sanitized = sanitizer.sanitize(document.html);

        Document doc = Jsoup.parseBodyFragment(sanitized);

        // Every element the document authored with allowed formatting must still be present
        // in the sanitized body, with the same tag name it was authored with (Req 2.3).
        for (String tag : document.expectedTags) {
            assertThat(doc.getElementsByTag(tag))
                    .as("allowed-formatting element <%s> must survive sanitization for input <%s>",
                            tag, document.html)
                    .isNotEmpty();
        }

        // The allowed-scheme anchor keeps its href so the hyperlink formatting is retained.
        if (document.expectedTags.contains("a")) {
            assertThat(doc.getElementsByTag("a").first().hasAttr("href"))
                    .as("allowed-scheme hyperlink must keep its href for input <%s>", document.html)
                    .isTrue();
        }
    }

    /** A generated document plus the set of allowed-formatting tags it is known to contain. */
    private static final class FormattingDocument {
        final String html;
        final List<String> expectedTags;

        FormattingDocument(String html, List<String> expectedTags) {
            this.html = html;
            this.expectedTags = expectedTags;
        }
    }

    /** A single allowed-formatting fragment paired with the tag name it introduces. */
    private static final class Fragment {
        final String html;
        final String tag;

        Fragment(String html, String tag) {
            this.html = html;
            this.tag = tag;
        }
    }

    /**
     * Generates documents composed only of allowed-formatting elements: emphasis, headings,
     * ordered/unordered lists, paragraphs, line breaks, and allowed-scheme links.
     */
    @Provide
    Arbitrary<FormattingDocument> allowedFormattingDocument() {
        // Between 1 and 8 allowed-formatting fragments, concatenated into one document.
        return allowedFragment().list().ofMinSize(1).ofMaxSize(8)
                .map(fragments -> {
                    StringBuilder html = new StringBuilder();
                    List<String> tags = new ArrayList<>();
                    for (Fragment f : fragments) {
                        html.append(f.html);
                        tags.add(f.tag);
                    }
                    return new FormattingDocument(html.toString(), tags);
                });
    }

    /** Random markup drawn exclusively from the allowed-formatting allow-list. */
    private Arbitrary<Fragment> allowedFragment() {
        Arbitrary<String> text = safeText();
        return Arbitraries.oneOf(
                // Inline emphasis: b vs strong, i vs em, u, s vs strike are each preserved as-authored.
                text.map(t -> new Fragment("<b>" + t + "</b>", "b")),
                text.map(t -> new Fragment("<strong>" + t + "</strong>", "strong")),
                text.map(t -> new Fragment("<i>" + t + "</i>", "i")),
                text.map(t -> new Fragment("<em>" + t + "</em>", "em")),
                text.map(t -> new Fragment("<u>" + t + "</u>", "u")),
                text.map(t -> new Fragment("<s>" + t + "</s>", "s")),
                text.map(t -> new Fragment("<strike>" + t + "</strike>", "strike")),
                // Headings h1..h6.
                text.map(t -> new Fragment("<h1>" + t + "</h1>", "h1")),
                text.map(t -> new Fragment("<h2>" + t + "</h2>", "h2")),
                text.map(t -> new Fragment("<h3>" + t + "</h3>", "h3")),
                text.map(t -> new Fragment("<h4>" + t + "</h4>", "h4")),
                text.map(t -> new Fragment("<h5>" + t + "</h5>", "h5")),
                text.map(t -> new Fragment("<h6>" + t + "</h6>", "h6")),
                // Block structure and line breaks.
                text.map(t -> new Fragment("<p>" + t + "</p>", "p")),
                text.map(t -> new Fragment(t + "<br>", "br")),
                // Lists (li items always accompany their ol/ul parent).
                Combinators.combine(text, text).as((a, b) ->
                        new Fragment("<ul><li>" + a + "</li><li>" + b + "</li></ul>", "ul")),
                Combinators.combine(text, text).as((a, b) ->
                        new Fragment("<ol><li>" + a + "</li><li>" + b + "</li></ol>", "ol")),
                // Allowed-scheme hyperlinks (http/https/mailto).
                Combinators.combine(allowedHref(), text).as((href, t) ->
                        new Fragment("<a href=\"" + href + "\">" + t + "</a>", "a")));
    }

    /** Hyperlink targets whose scheme is on the allow-list (http/https/mailto). */
    private Arbitrary<String> allowedHref() {
        return Arbitraries.of(
                "http://example.com/path",
                "https://example.com/secure",
                "mailto:owner@example.com");
    }

    /**
     * Plain text that never contains angle brackets, ampersands, or quotes, so it can never
     * accidentally introduce markup, and never empty so no element collapses to nothing that
     * a lenient parser might drop.
     */
    private Arbitrary<String> safeText() {
        return Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(1).ofMaxLength(24);
    }
}
