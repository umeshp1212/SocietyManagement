package com.society.module.owner.service;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link OwnerEmailSanitizer#sanitize(String)} hyperlink handling.
 *
 * <p><b>Feature: owner-email-rich-text, Property 2: Hyperlink scheme allow-list.</b>
 * For any anchor whose target uses an {@code Allowed_Url_Scheme}
 * ({@code http}/{@code https}/{@code mailto}), the {@code Sanitized_Body} retains the
 * {@code href}. For any anchor whose target uses a disallowed scheme (such as
 * {@code javascript} or {@code data}), the {@code Sanitized_Body} drops the {@code href}
 * while retaining the anchor's visible text.</p>
 *
 * <p><b>Validates: Requirements 3.5</b></p>
 */
class OwnerEmailSanitizerHyperlinkSchemePropertyTest {

    private final OwnerEmailSanitizer sanitizer = new OwnerEmailSanitizer();

    /** Schemes that the sanitizer allows on {@code <a href>} (Req 3.5). */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto");

    // Feature: owner-email-rich-text, Property 2: Hyperlink scheme allow-list
    @Property(tries = 100)
    void allowedSchemesKeepHrefAndDisallowedSchemesDropHrefButKeepText(
            @ForAll("anchor") AnchorCase anchorCase) {

        String sanitized = sanitizer.sanitize(anchorCase.html);

        Document doc = Jsoup.parseBodyFragment(sanitized);

        // Visible text is preserved regardless of the scheme (Req 3.5): whether or not the
        // anchor wrapper survives, the human-readable content must remain in the output.
        assertThat(doc.text())
                .as("anchor visible text must be preserved for input <%s>", anchorCase.html)
                .isEqualTo(anchorCase.visibleText);

        if (anchorCase.allowed) {
            // Allowed scheme -> the anchor is retained with its href, and that href uses an
            // allowed scheme.
            List<Element> anchors = doc.getElementsByTag("a");
            assertThat(anchors)
                    .as("allowed-scheme anchor must survive for input <%s>", anchorCase.html)
                    .hasSize(1);
            Element anchor = anchors.get(0);
            assertThat(anchor.hasAttr("href"))
                    .as("allowed-scheme anchor must keep its href for input <%s>", anchorCase.html)
                    .isTrue();
            String href = anchor.attr("href");
            assertThat(scheme(href))
                    .as("retained href '%s' must use an allowed scheme", href)
                    .isIn(ALLOWED_SCHEMES);
        } else {
            // Disallowed scheme -> the hyperlink target (href) is removed entirely; no anchor
            // in the output may carry an href, and the disallowed URL must not survive
            // anywhere in the sanitized markup (Req 3.5).
            for (Element anchor : doc.getElementsByTag("a")) {
                assertThat(anchor.hasAttr("href"))
                        .as("disallowed-scheme anchor must not keep any href for input <%s>", anchorCase.html)
                        .isFalse();
            }
            assertThat(sanitized)
                    .as("disallowed hyperlink target must be removed for input <%s>", anchorCase.html)
                    .doesNotContain(anchorCase.href);
        }
    }

    /** A generated anchor together with the facts the property asserts about it. */
    private static final class AnchorCase {
        final String html;
        final String href;
        final String visibleText;
        final boolean allowed;

        AnchorCase(String html, String href, String visibleText, boolean allowed) {
            this.html = html;
            this.href = href;
            this.visibleText = visibleText;
            this.allowed = allowed;
        }
    }

    /**
     * Generates anchors with random allowed and disallowed schemes plus random visible text.
     */
    @Provide
    Arbitrary<AnchorCase> anchor() {
        Arbitrary<SchemeCase> schemes = Arbitraries.oneOf(
                // Allowed schemes with realistic targets.
                Arbitraries.just(new SchemeCase("http://example.com/path", true)),
                Arbitraries.just(new SchemeCase("https://example.com/secure", true)),
                Arbitraries.just(new SchemeCase("mailto:owner@example.com", true)),
                // Disallowed schemes that must have their href dropped.
                Arbitraries.just(new SchemeCase("javascript:alert(1)", false)),
                Arbitraries.just(new SchemeCase("data:text/html,<b>x</b>", false)),
                Arbitraries.just(new SchemeCase("vbscript:msgbox(1)", false)),
                Arbitraries.just(new SchemeCase("file:///etc/passwd", false)),
                Arbitraries.just(new SchemeCase("ftp://example.com/file", false)),
                Arbitraries.just(new SchemeCase("tel:+15551234567", false)));

        return Combinators.combine(schemes, visibleText())
                .as((sc, text) -> new AnchorCase(
                        "<a href=\"" + sc.href + "\">" + text + "</a>", sc.href, text, sc.allowed));
    }

    /** A candidate href target paired with whether its scheme is on the allow-list. */
    private static final class SchemeCase {
        final String href;
        final boolean allowed;

        SchemeCase(String href, boolean allowed) {
            this.href = href;
            this.allowed = allowed;
        }
    }

    /**
     * Visible anchor text that never contains angle brackets, ampersands, quotes, or
     * whitespace, so it cannot introduce markup, alter the parsed structure, or be altered
     * by HTML whitespace normalization (which would collapse or trim spaces and make the
     * round-trip comparison brittle without changing the meaningful content).
     */
    private Arbitrary<String> visibleText() {
        return Arbitraries.strings().alpha().numeric().withChars('.', ',')
                .ofMinLength(1).ofMaxLength(30);
    }

    /** Extracts the lower-cased scheme (portion before the first {@code :}) of a URL. */
    private static String scheme(String url) {
        int colon = url.indexOf(':');
        return colon < 0 ? "" : url.substring(0, colon).toLowerCase(Locale.ROOT);
    }
}
