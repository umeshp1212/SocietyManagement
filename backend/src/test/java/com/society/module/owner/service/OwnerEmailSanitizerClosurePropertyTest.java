package com.society.module.owner.service;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link OwnerEmailSanitizer#sanitize(String)}.
 *
 * <p><b>Feature: owner-email-rich-text, Property 1: Sanitization allow-list closure.</b>
 * For any {@code Rich_Text_Body} — including one that always injects dangerous
 * {@code script}/{@code iframe}/{@code object}/{@code embed}/{@code style}/{@code link}
 * elements and {@code on*} event-handler attributes alongside random allowed and
 * disallowed markup — the produced {@code Sanitized_Body} contains only elements and
 * attributes within the allow-list, and none of the injected dangerous elements,
 * event-handler attributes, or inline script survive.</p>
 *
 * <p><b>Validates: Requirements 3.2, 3.3, 3.4, 3.6</b></p>
 */
class OwnerEmailSanitizerClosurePropertyTest {

    private final OwnerEmailSanitizer sanitizer = new OwnerEmailSanitizer();

    /** The complete element allow-list enforced by {@link OwnerEmailSanitizer}. */
    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
            "b", "strong", "i", "em", "u", "s", "strike",
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "ul", "ol", "li", "a");

    /** The only attribute the allow-list permits, and only on {@code <a>}. */
    private static final String ALLOWED_ATTR_ON_ANCHOR = "href";

    /** Elements that must never survive sanitization (Req 3.3). */
    private static final Set<String> DANGEROUS_ELEMENTS = Set.of(
            "script", "iframe", "object", "embed", "style", "link");

    // Feature: owner-email-rich-text, Property 1: Sanitization allow-list closure
    @Property(tries = 100)
    void sanitizedBodyContainsOnlyAllowListMarkupAndNoInjectedDanger(
            @ForAll("richTextWithInjectedDanger") String richTextBody) {

        String sanitized = sanitizer.sanitize(richTextBody);

        // Parse the sanitizer output as an HTML fragment; walk every surviving element.
        Document doc = Jsoup.parseBodyFragment(sanitized);
        List<Element> survivors = doc.body().getAllElements();

        for (Element el : survivors) {
            String tag = el.tagName().toLowerCase(Locale.ROOT);
            if (tag.equals("body") || tag.equals("html") || tag.equals("head")) {
                // Wrapper nodes introduced by the parser, not part of the sanitizer output.
                continue;
            }

            // (a) Closure: every surviving element is in the allow-list (Req 3.2),
            //     which structurally guarantees no dangerous element survives (Req 3.3).
            assertThat(ALLOWED_ELEMENTS)
                    .as("surviving element <%s> must be within the allow-list", tag)
                    .contains(tag);
            assertThat(DANGEROUS_ELEMENTS)
                    .as("dangerous element <%s> must not survive", tag)
                    .doesNotContain(tag);

            // (b) Closure: every surviving attribute is allowed; the only permitted
            //     attribute is href on <a>. No on* event handler survives (Req 3.4),
            //     because rel is auto-added by requireRelNofollowOnLinks() on anchors.
            for (Attribute attr : el.attributes()) {
                String name = attr.getKey().toLowerCase(Locale.ROOT);
                assertThat(name)
                        .as("surviving attribute '%s' on <%s> must not be an event handler", name, tag)
                        .doesNotStartWith("on");
                boolean isAllowedAnchorHref = tag.equals("a")
                        && (name.equals(ALLOWED_ATTR_ON_ANCHOR) || name.equals("rel"));
                assertThat(isAllowedAnchorHref)
                        .as("surviving attribute '%s' on <%s> must be within the allow-list", name, tag)
                        .isTrue();
            }
        }

        // (c) No injected dangerous element survives anywhere in the output.
        for (String dangerous : DANGEROUS_ELEMENTS) {
            assertThat(doc.getElementsByTag(dangerous))
                    .as("no <%s> element may survive sanitization", dangerous)
                    .isEmpty();
        }

        // (d) No inline script text survives: the sanitizer drops <script>/<style>
        //     elements together with their text content, so the JS marker payload
        //     that was injected inside them must not appear anywhere in the output.
        assertThat(sanitized)
                .as("injected inline script payload must not survive")
                .doesNotContain(JS_MARKER);
    }

    /** A distinctive JS payload placed inside injected script/style bodies and on* handlers. */
    private static final String JS_MARKER = "xssPwn__(1)";

    /**
     * Always injects dangerous {@code script}/{@code iframe}/{@code object}/{@code embed}/
     * {@code style}/{@code link} elements and {@code on*} event-handler attributes, then
     * interleaves random allowed and disallowed markup around them.
     */
    @Provide
    Arbitrary<String> richTextWithInjectedDanger() {
        Arbitrary<String> danger = dangerousFragment();
        Arbitrary<String> allowed = allowedFragment();
        Arbitrary<String> disallowed = disallowedFragment();

        // A body is a shuffled-ish concatenation: at least one dangerous fragment is
        // always present, sandwiched between random allowed/disallowed markup.
        return Combinators.combine(
                        allowed, disallowed, danger, allowed, danger, disallowed)
                .as((a1, d1, x1, a2, x2, d2) -> a1 + d1 + x1 + a2 + x2 + d2);
    }

    /** Dangerous markup that must be fully stripped: forbidden elements + on* handlers. */
    private Arbitrary<String> dangerousFragment() {
        Arbitrary<String> text = safeText();
        return Arbitraries.oneOf(
                text.map(t -> "<script>" + JS_MARKER + ";</script>" + t),
                text.map(t -> "<style>body{content:'" + JS_MARKER + "'}</style>" + t),
                text.map(t -> "<iframe src=\"javascript:" + JS_MARKER + "\"></iframe>" + t),
                text.map(t -> "<object data=\"evil.swf\">" + t + "</object>"),
                text.map(t -> "<embed src=\"evil.swf\">" + t),
                text.map(t -> "<link rel=\"stylesheet\" href=\"evil.css\">" + t),
                text.map(t -> "<p onclick=\"" + JS_MARKER + "\">" + t + "</p>"),
                text.map(t -> "<a href=\"http://ok\" onerror=\"" + JS_MARKER + "\">" + t + "</a>"),
                text.map(t -> "<img src=x onerror=\"" + JS_MARKER + "\">" + t));
    }

    /** Random markup drawn from the allow-list. */
    private Arbitrary<String> allowedFragment() {
        Arbitrary<String> text = safeText();
        return Arbitraries.oneOf(
                text.map(t -> "<b>" + t + "</b>"),
                text.map(t -> "<strong>" + t + "</strong>"),
                text.map(t -> "<i>" + t + "</i>"),
                text.map(t -> "<em>" + t + "</em>"),
                text.map(t -> "<u>" + t + "</u>"),
                text.map(t -> "<s>" + t + "</s>"),
                text.map(t -> "<h1>" + t + "</h1>"),
                text.map(t -> "<h3>" + t + "</h3>"),
                text.map(t -> "<p>" + t + "</p>"),
                text.map(t -> "<ul><li>" + t + "</li></ul>"),
                text.map(t -> "<ol><li>" + t + "</li></ol>"),
                text.map(t -> t + "<br>"),
                text.map(t -> "<a href=\"https://example.com\">" + t + "</a>"));
    }

    /** Random disallowed-but-not-explicitly-dangerous markup (dropped by the allow-list). */
    private Arbitrary<String> disallowedFragment() {
        Arbitrary<String> text = safeText();
        return Arbitraries.oneOf(
                text.map(t -> "<div>" + t + "</div>"),
                text.map(t -> "<span style=\"color:red\">" + t + "</span>"),
                text.map(t -> "<table><tr><td>" + t + "</td></tr></table>"),
                text.map(t -> "<font size=\"7\">" + t + "</font>"),
                text.map(t -> "<marquee>" + t + "</marquee>"),
                text.map(t -> "<form action=\"x\">" + t + "</form>"));
    }

    /**
     * Plain text that never contains angle brackets, ampersands, or quotes, so it can
     * never accidentally introduce markup or collide with the JS marker payload.
     */
    private Arbitrary<String> safeText() {
        return Arbitraries.strings().alpha().numeric().withChars(' ', '.', ',')
                .ofMinLength(1).ofMaxLength(24);
    }
}
