package com.society.module.ownernoc.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.society.module.ownernoc.entity.OwnerNocRequest;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates a general-purpose "No Objection Certificate" PDF for an owner NOC
 * request (loan transfer, property-tax name change, electricity-bill name change,
 * passport/residence certificate, etc.). The certificate body is arbitrary text
 * (pre-filled from the NOC type template and optionally edited by the approving
 * admin), so the wording can vary from request to request (e.g. bank to bank).
 *
 * Modeled on the iText 7 patterns used by the tenant NocCertificatePdfService.
 */
@Service
@RequiredArgsConstructor
public class OwnerNocPdfService {

    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);

    /**
     * Build the NOC certificate PDF for an approved owner NOC request.
     *
     * @return PDF bytes
     */
    public byte[] generate(OwnerNocRequest request) throws IOException {
        SocietySettings settings = settingsService.getSettings();
        String societyName = settings.getSocietyName();
        String ownerName = request.getOwner() != null && request.getOwner().getFullName() != null
                ? request.getOwner().getFullName() : "Owner";
        String unitLabel = request.getUnit() != null ? request.getUnit().getUnitNumber() : null;
        String typeName = request.getNocType() != null ? request.getNocType().getName() : "No Objection Certificate";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 45, 36, 45);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // ===== SOCIETY HEADER =====
        addSocietyHeader(document, settings, boldFont, regularFont);

        // ===== TITLE =====
        document.add(new Paragraph("NO OBJECTION CERTIFICATE")
                .setFont(boldFont).setFontSize(15)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(14).setMarginBottom(2));
        document.add(new Paragraph("(" + typeName + ")")
                .setFont(italicFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(16));

        // ===== REF + DATE =====
        Table refTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        String typeCode = request.getNocType() != null ? request.getNocType().getCode() : "GEN";
        refTable.addCell(borderlessCell("Ref: NOC/" + typeCode + "/" + request.getRequestId(),
                regularFont, 10, TextAlignment.LEFT));
        refTable.addCell(borderlessCell("Date: " + LocalDate.now().format(DATE_FORMAT),
                regularFont, 10, TextAlignment.RIGHT));
        document.add(refTable);

        // ===== ADDRESSEE =====
        if (request.getAddressee() != null && !request.getAddressee().isBlank()) {
            document.add(new Paragraph("To,").setFont(regularFont).setFontSize(11).setMarginTop(6));
            document.add(new Paragraph(request.getAddressee())
                    .setFont(boldFont).setFontSize(11).setMarginBottom(10));
        } else {
            document.add(new Paragraph("\n"));
        }

        // ===== BODY (arbitrary text, resolved from template / admin edits) =====
        String body = resolveBody(request, settings, ownerName, unitLabel);
        for (String para : body.split("\\r?\\n")) {
            if (para.isBlank()) {
                document.add(new Paragraph("\n").setFontSize(4));
            } else {
                document.add(new Paragraph(para)
                        .setFont(regularFont).setFontSize(11)
                        .setTextAlignment(TextAlignment.JUSTIFIED)
                        .setMarginBottom(8));
            }
        }

        // ===== DETAILS TABLE =====
        Table details = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8).setMarginBottom(16);
        addDetailRow(details, "Owner Name", ownerName, boldFont, regularFont);
        if (unitLabel != null) {
            addDetailRow(details, "Unit / Flat No.", unitLabel, boldFont, regularFont);
        }
        addDetailRow(details, "Certificate Type", typeName, boldFont, regularFont);
        if (request.getReviewedBy() != null) {
            addDetailRow(details, "Approved By", request.getReviewedBy(), boldFont, regularFont);
        }
        document.add(details);

        // ===== DIGITAL SIGNATURE (no manual signature line; designation only) =====
        Table sign = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        sign.addCell(borderlessCell("", regularFont, 10, TextAlignment.LEFT));
        Cell signCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER);
        signCell.add(buildDigitalSignatureStamp(societyName, boldFont, regularFont, italicFont));
        signCell.add(new Paragraph("Secretary / Authorised Signatory")
                .setFont(boldFont).setFontSize(10).setMarginTop(4));
        signCell.add(new Paragraph("For " + societyName).setFont(regularFont).setFontSize(9));
        sign.addCell(signCell);
        document.add(sign);

        // ===== FOOTER =====
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("This is a computer-generated certificate.")
                .setFont(italicFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        document.close();
        return baos.toByteArray();
    }

    /**
     * Resolve the certificate body. Prefers the admin-finalized {@code finalContent};
     * otherwise falls back to the NOC type default template; otherwise a generic body.
     * Simple placeholders are substituted in all cases.
     */
    private String resolveBody(OwnerNocRequest request, SocietySettings settings,
                               String ownerName, String unitLabel) {
        String template = request.getFinalContent();
        if (template == null || template.isBlank()) {
            template = request.getNocType() != null ? request.getNocType().getDefaultTemplate() : null;
        }
        if (template == null || template.isBlank()) {
            template = "This is to certify that the Managing Committee of {societyName} has NO OBJECTION "
                    + "to {ownerName}" + (unitLabel != null ? ", owner of Unit No. {unitNumber}," : "")
                    + " for the purpose stated below.\n{details}";
        }
        return template
                .replace("{ownerName}", ownerName != null ? ownerName : "")
                .replace("{unitNumber}", unitLabel != null ? unitLabel : "")
                .replace("{societyName}", settings.getSocietyName() != null ? settings.getSocietyName() : "")
                .replace("{registrationNumber}", settings.getRegistrationNumber() != null ? settings.getRegistrationNumber() : "")
                .replace("{addressee}", request.getAddressee() != null ? request.getAddressee() : "")
                .replace("{details}", request.getDetails() != null ? request.getDetails() : "")
                .replace("{date}", LocalDate.now().format(DATE_FORMAT));
    }

    private void addSocietyHeader(Document document, SocietySettings settings,
                                  PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph(settings.getSocietyName())
                .setFont(boldFont).setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));

        if (settings.getRegistrationNumber() != null) {
            document.add(new Paragraph("Reg. No: " + settings.getRegistrationNumber()
                    + (settings.getRegistrationDate() != null ? " | Reg. Date: " + settings.getRegistrationDate() : ""))
                    .setFont(regularFont).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));
        }

        StringBuilder address = new StringBuilder();
        if (settings.getAddressLine1() != null) address.append(settings.getAddressLine1());
        if (settings.getAddressLine2() != null && !settings.getAddressLine2().isBlank())
            address.append(", ").append(settings.getAddressLine2());
        if (settings.getCity() != null) address.append(", ").append(settings.getCity());
        if (settings.getPincode() != null) address.append(" - ").append(settings.getPincode());
        if (settings.getState() != null) address.append(", ").append(settings.getState());
        if (address.length() > 0) {
            document.add(new Paragraph(address.toString())
                    .setFont(regularFont).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));
        }

        if (settings.getPhone() != null || settings.getEmail() != null) {
            document.add(new Paragraph("Phone: " + (settings.getPhone() != null ? settings.getPhone() : "-")
                    + " | Email: " + (settings.getEmail() != null ? settings.getEmail() : "-"))
                    .setFont(regularFont).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(5));
        }

        Table line = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        line.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(HEADER_BG, 2)).setHeight(1));
        document.add(line);
    }

    private void addDetailRow(Table table, String label, String value,
                              PdfFont boldFont, PdfFont regularFont) {
        table.addCell(new Cell().add(new Paragraph(label).setFont(boldFont).setFontSize(10))
                .setPadding(6).setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)));
        table.addCell(new Cell().add(new Paragraph(value != null ? value : "-").setFont(regularFont).setFontSize(10))
                .setPadding(6).setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)));
    }

    private Cell borderlessCell(String text, PdfFont font, int fontSize, TextAlignment alignment) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(fontSize)
                .setTextAlignment(alignment)).setBorder(Border.NO_BORDER);
    }

    /**
     * Builds a text-based digital-signature stamp (no manual signature line).
     * Renders a bordered box indicating the certificate was digitally signed by
     * the society, with a timestamp.
     */
    private Table buildDigitalSignatureStamp(String societyName, PdfFont boldFont,
                                             PdfFont regularFont, PdfFont italicFont) {
        DeviceRgb green = new DeviceRgb(46, 125, 50);
        String stamp = "Digitally signed by " + societyName + "\n"
                + "Date: " + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        Table box = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(90))
                .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                .setMarginTop(6);
        Cell c = new Cell()
                .add(new Paragraph("DIGITALLY SIGNED").setFont(boldFont).setFontSize(9).setFontColor(green))
                .add(new Paragraph(stamp).setFont(italicFont).setFontSize(8).setFontColor(green))
                .setBorder(new SolidBorder(green, 1))
                .setPadding(6)
                .setTextAlignment(TextAlignment.CENTER);
        box.addCell(c);
        return box;
    }
}
