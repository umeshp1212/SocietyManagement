package com.society.module.tenant.service;

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
import com.society.module.owner.entity.Owner;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

/**
 * Generates a "No Objection Certificate" (NOC) PDF that the society issues to the
 * flat owner once a tenant registration is approved. This is the document that
 * permits the tenant to move in / shift into the society.
 *
 * Modeled on the iText 7 patterns used by {@code VoucherPdfService}.
 */
@Service
@RequiredArgsConstructor
public class NocCertificatePdfService {

    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);

    /**
     * Build the No Objection Certificate PDF for a tenant.
     *
     * @return PDF bytes
     */
    public byte[] generateNocCertificate(Tenant tenant) throws IOException {
        SocietySettings settings = settingsService.getSettings();
        Owner owner = tenant.getUnit().getPrimaryOwner();
        String ownerName = owner != null ? owner.getFullName() : tenant.getUnit().getOwnerNames();
        if (ownerName == null || ownerName.isBlank()) {
            ownerName = "Owner";
        }

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
        document.add(new Paragraph("(For Tenant Occupancy)")
                .setFont(italicFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(16));

        // ===== REF + DATE =====
        Table refTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        refTable.addCell(borderlessCell(
                "Ref: NOC/" + tenant.getUnit().getUnitNumber() + "/" + tenant.getTenantId(),
                regularFont, 10, TextAlignment.LEFT));
        refTable.addCell(borderlessCell(
                "Date: " + LocalDate.now().format(DATE_FORMAT),
                regularFont, 10, TextAlignment.RIGHT));
        document.add(refTable);
        document.add(new Paragraph("\n"));

        // ===== BODY =====
        String societyName = settings.getSocietyName();
        String unitLabel = tenant.getUnit().getUnitNumber();
        String agreementStart = tenant.getRentStartDate() != null
                ? tenant.getRentStartDate().format(DATE_FORMAT) : "N/A";
        String agreementEnd = tenant.getRentEndDate() != null
                ? tenant.getRentEndDate().format(DATE_FORMAT) : "N/A";

        String body = "This is to certify that the Managing Committee of " + societyName +
                " has NO OBJECTION to Mr./Ms. " + tenant.getTenantName() +
                " residing as a tenant in Flat/Unit No. " + unitLabel +
                ", owned by " + ownerName + ".";
        document.add(new Paragraph(body)
                .setFont(regularFont).setFontSize(11)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginBottom(10));

        document.add(new Paragraph(
                "The tenant is permitted to shift into and occupy the said premises for the tenancy " +
                "period commencing " + agreementStart + " and ending " + agreementEnd + ", " +
                "subject to compliance with the society's bye-laws, rules and regulations.")
                .setFont(regularFont).setFontSize(11)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginBottom(16));

        // ===== DETAILS TABLE =====
        Table details = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(16);
        addDetailRow(details, "Owner Name", ownerName, boldFont, regularFont);
        addDetailRow(details, "Unit / Flat No.", unitLabel, boldFont, regularFont);
        addDetailRow(details, "Tenant Name", tenant.getTenantName(), boldFont, regularFont);
        addDetailRow(details, "Tenant Contact", tenant.getContactNumber(), boldFont, regularFont);
        addDetailRow(details, "Agreement Start", agreementStart, boldFont, regularFont);
        addDetailRow(details, "Agreement End", agreementEnd, boldFont, regularFont);
        if (tenant.getNocApprovedBy() != null) {
            addDetailRow(details, "Approved By", tenant.getNocApprovedBy(), boldFont, regularFont);
        }
        document.add(details);

        document.add(new Paragraph(
                "Note: A non-occupancy charge, as applicable per the society's bye-laws, shall be levied " +
                "on the maintenance bill for the above unit for the duration of the tenancy.")
                .setFont(italicFont).setFontSize(9)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(24));

        // ===== SIGNATURE =====
        Table sign = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        sign.addCell(borderlessCell("", regularFont, 10, TextAlignment.LEFT));
        Cell signCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER);
        signCell.add(new Paragraph("\n\n_____________________").setFont(regularFont).setFontSize(10));
        signCell.add(new Paragraph("Authorised Signatory").setFont(boldFont).setFontSize(10));
        String secretary = settings.getSecretaryName();
        if (secretary != null && !secretary.isBlank()) {
            signCell.add(new Paragraph(secretary + " (Secretary)").setFont(regularFont).setFontSize(9));
        }
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
}
