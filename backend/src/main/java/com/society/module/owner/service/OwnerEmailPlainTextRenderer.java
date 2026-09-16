package com.society.module.owner.service;

import com.society.module.settings.entity.SocietySettings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Renders the {@code Plain_Text_Alternative} of an owner email from the already
 * sanitized HTML body, mirroring the structure the {@link OwnerEmailTemplateBuilder}
 * uses for the HTML part: {@code Society_Header} then {@code Body} (subject + message)
 * then {@code Footer}, in that fixed order.
 *
 * <p>The message content is produced by walking the sanitized DOM with Jsoup so this
 * component operates only on the {@code Sanitized_Body} and never touches the raw
 * {@code Rich_Text_Body}. Block-level breaks ({@code <p>}/{@code <br>} and headings)
 * become newlines, ordered-list items become {@code "1. "}, {@code "2. "}, ...
 * (numbered, incrementing per list), and unordered-list items become {@code "- "},
 * so the structure remains readable in clients that do not render HTML.
 *
 * Requirements: 7.2, 7.3
 */
@Component
public class OwnerEmailPlainTextRenderer {

    private static final String LINE = System.lineSeparator();
    private static final String SECTION_BREAK = LINE + LINE;

    /**
     * Renders the plain-text alternative from the sanitized HTML body.
     *
     * @param sanitizedHtml the {@code Sanitized_Body} (safe HTML); may be null/blank
     * @param subject       the user-entered subject
     * @param settings      the society settings supplying header and footer content
     * @return the plain-text rendering in header -> body -> footer order
     */
    public String render(String sanitizedHtml, String subject, SocietySettings settings) {
        StringBuilder content = new StringBuilder();

        String header = buildHeader(settings);
        if (StringUtils.hasText(header)) {
            content.append(header);
            content.append(SECTION_BREAK);
        }

        content.append(buildBodySection(subject, sanitizedHtml));

        String footer = buildFooter(settings);
        if (StringUtils.hasText(footer)) {
            content.append(SECTION_BREAK);
            content.append(footer);
        }

        return content.toString();
    }

    // ----- Header / Footer (mirror the HTML template order and fields) -----

    private String buildHeader(SocietySettings settings) {
        if (settings == null) {
            return "";
        }
        StringBuilder header = new StringBuilder();
        appendLine(header, settings.getSocietyName());

        String address = buildAddress(settings);
        appendLine(header, address);

        if (StringUtils.hasText(settings.getRegistrationNumber())) {
            appendLine(header, "Reg. No: " + settings.getRegistrationNumber().trim());
        }
        if (StringUtils.hasText(settings.getPhone())) {
            appendLine(header, "Phone: " + settings.getPhone().trim());
        }
        if (StringUtils.hasText(settings.getEmail())) {
            appendLine(header, "Email: " + settings.getEmail().trim());
        }
        return header.toString();
    }

    private String buildAddress(SocietySettings settings) {
        StringBuilder addressLine = new StringBuilder();
        appendPart(addressLine, settings.getAddressLine1());
        appendPart(addressLine, settings.getAddressLine2());
        appendPart(addressLine, settings.getCity());
        appendPart(addressLine, settings.getState());
        appendPart(addressLine, settings.getPincode());
        return addressLine.toString();
    }

    private String buildFooter(SocietySettings settings) {
        if (settings == null) {
            return "";
        }
        StringBuilder footer = new StringBuilder();
        footer.append("Regards,");
        if (StringUtils.hasText(settings.getChairmanName())) {
            appendLine(footer, "Chairman: " + settings.getChairmanName().trim());
        }
        if (StringUtils.hasText(settings.getSecretaryName())) {
            appendLine(footer, "Secretary: " + settings.getSecretaryName().trim());
        }
        if (StringUtils.hasText(settings.getTreasurerName())) {
            appendLine(footer, "Treasurer: " + settings.getTreasurerName().trim());
        }
        return footer.toString();
    }

    private String buildBodySection(String subject, String sanitizedHtml) {
        StringBuilder body = new StringBuilder();
        body.append("Subject: ").append(subject == null ? "" : subject.trim());
        body.append(SECTION_BREAK);
        body.append(renderMessage(sanitizedHtml));
        return body.toString();
    }

    // ----- Sanitized-body DOM walk (Req 7.2, 7.3) -----

    /**
     * Walks the sanitized HTML DOM, turning block-level breaks into newlines and list
     * items into their plain-text markers ({@code "1. "}/{@code "2. "}... for {@code <ol>},
     * {@code "- "} for {@code <ul>}), then collapses redundant blank lines.
     */
    private String renderMessage(String sanitizedHtml) {
        if (!StringUtils.hasText(sanitizedHtml)) {
            return "";
        }
        Document document = Jsoup.parseBodyFragment(sanitizedHtml);
        StringBuilder out = new StringBuilder();
        walk(document.body(), out);
        return normalise(out.toString());
    }

    private void walk(Node node, StringBuilder out) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                out.append(((TextNode) child).text());
            } else if (child instanceof Element) {
                renderElement((Element) child, out);
            }
        }
    }

    private void renderElement(Element element, StringBuilder out) {
        String tag = element.normalName();
        switch (tag) {
            case "br":
                out.append(LINE);
                break;
            case "ol":
                renderList(element, out, true);
                break;
            case "ul":
                renderList(element, out, false);
                break;
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                walk(element, out);
                out.append(SECTION_BREAK);
                break;
            default:
                // inline formatting (b, strong, i, em, u, s, a, ...) contributes its text only
                walk(element, out);
                break;
        }
    }

    private void renderList(Element list, StringBuilder out, boolean ordered) {
        int index = 1;
        for (Element item : list.children()) {
            if (!"li".equals(item.normalName())) {
                continue;
            }
            if (ordered) {
                out.append(index).append(". ");
                index++;
            } else {
                out.append("- ");
            }
            walk(item, out);
            out.append(LINE);
        }
        out.append(LINE);
    }

    // ----- Helpers -----

    private void appendLine(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(LINE);
        }
        builder.append(value.trim());
    }

    private void appendPart(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.trim());
        }
    }

    /**
     * Collapses runs of three or more newlines into a single blank line and trims
     * leading/trailing whitespace so the rendered message stays readable.
     */
    private String normalise(String text) {
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        String collapsed = unified.replaceAll("\n{3,}", "\n\n");
        collapsed = collapsed.replaceAll("[ \t]+\n", "\n");
        return collapsed.trim().replace("\n", LINE);
    }
}
