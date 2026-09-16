package com.society.module.owner.service;

import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Authoritative server-side sanitizer for the owner-email rich-text body.
 *
 * <p>Transforms a {@code Rich_Text_Body} (arbitrary HTML produced by the client editor)
 * into a {@code Sanitized_Body} that contains only the safe allow-list of formatting.
 * The allow-list model (OWASP Java HTML Sanitizer) is structural: only explicitly
 * permitted elements/attributes survive, so {@code script}, {@code iframe},
 * {@code object}, {@code embed}, {@code style}, {@code link}, {@code on*} handlers,
 * and any other unlisted markup are dropped without needing to be enumerated.
 *
 * <p>Hyperlinks are permitted only on {@code <a href>} whose scheme is
 * {@code http}, {@code https}, or {@code mailto}; a disallowed scheme causes the
 * {@code href} to be dropped while the anchor's visible text is retained.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Component
public class OwnerEmailSanitizer {

    /**
     * Allow-list matching {@code Allowed_Formatting}. Everything not named here is dropped.
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            // inline emphasis
            .allowElements("b", "strong", "i", "em", "u", "s", "strike")
            // headings + block structure + lists
            .allowElements("h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "ul", "ol", "li")
            // hyperlinks restricted to safe schemes (Req 3.5, D1)
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            .requireRelNofollowOnLinks()
            .toFactory();

    /**
     * Transforms a {@code Rich_Text_Body} into a {@code Sanitized_Body} containing only
     * {@code Allowed_Formatting}. A null or blank input yields an empty string. Never
     * throws on malformed markup (the sanitizer tolerates and repairs bad HTML).
     *
     * @param richTextBody the raw HTML produced by the client editor (may be null)
     * @return the sanitized allow-list HTML, or {@code ""} for null/blank input
     */
    public String sanitize(String richTextBody) {
        if (richTextBody == null || richTextBody.isBlank()) {
            return "";
        }
        return POLICY.sanitize(richTextBody);
    }

    /**
     * Returns the visible (plain-text) content of sanitized HTML, with tags removed,
     * entities decoded, and surrounding whitespace trimmed. Used to enforce the
     * 1..10000 visible-text bound (Req 4.4, 4.5).
     *
     * @param sanitizedHtml the sanitized HTML (may be null)
     * @return the trimmed visible text, or {@code ""} for null input
     */
    public String visibleText(String sanitizedHtml) {
        if (sanitizedHtml == null) {
            return "";
        }
        return Jsoup.parse(sanitizedHtml).text().trim();
    }
}
