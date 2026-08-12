package com.society.module.vendor.service;

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
import com.itextpdf.layout.properties.VerticalAlignment;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.vendor.dto.VendorLedgerDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class VendorLedgerPdfService {

    private final VendorService vendorService;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb LIGHT_GRAY_BG = new DeviceRgb(245, 245, 245);

    public byte[] generateLedgerPdf(Long vendorId, LocalDate startDate, LocalDate endDate) throws IOException {
        VendorLedgerDTO ledger = vendorService.getVendorLedger(vendorId, startDate, endDate);
        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4.rotate());
        document.setMargins(25, 30, 25, 30);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // ===== SOCIETY HEADER =====
        addSocietyHeader(document, settings, boldFont, regularFont);

        // ===== LEDGER TITLE =====
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("VENDOR LEDGER")
                .setFont(boldFont).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5));

        // ===== VENDOR INFO =====
        document.add(new Paragraph("Vendor: " + ledger.getVendorName())
                .setFont(boldFont).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));

        String period = "All Transactions";
        if (startDate != null && endDate != null) {
            period = "Period: " + startDate.format(DATE_FORMAT) + " to " + endDate.format(DATE_FORMAT);
        }
        document.add(new Paragraph(period)
                .setFont(regularFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        // ===== SUMMARY =====
        document.add(new Paragraph("Total Payments: \u20B9 " + formatAmount(ledger.getTotalAmount())
                + "  |  Total Transactions: " + ledger.getEntries().size())
                .setFont(boldFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(15));

        // ===== LEDGER TABLE =====
        if (ledger.getEntries().isEmpty()) {
            document.add(new Paragraph("No transactions found for this vendor.")
                    .setFont(italicFont).setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20));
        } else {
            Table table = new Table(UnitValue.createPercentArray(new float[]{1.2f, 1.5f, 1.2f, 1.5f, 3f, 1.2f, 1.5f, 1.5f}))
                    .setWidth(UnitValue.createPercentValue(100));

            // Header row
            String[] headers = {"Date", "Voucher No", "Type", "Category", "Description", "Payment Mode", "Amount", "Running Total"};
            for (String header : headers) {
                table.addHeaderCell(new Cell()
                        .add(new Paragraph(header).setFont(boldFont).setFontSize(8)
                                .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(HEADER_BG)
                        .setPadding(5)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE));
            }

            // Data rows
            boolean alternate = false;
            for (VendorLedgerDTO.LedgerEntry entry : ledger.getEntries()) {
                DeviceRgb rowBg = alternate ? LIGHT_GRAY_BG : null;

                table.addCell(createDataCell(entry.getVoucherDate().format(DATE_FORMAT), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(entry.getVoucherNumber(), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(entry.getVoucherType(), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(entry.getCategory().replace("_", " "), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(truncate(entry.getDescription(), 40), regularFont, rowBg, TextAlignment.LEFT));
                table.addCell(createDataCell(entry.getPaymentMode() != null ? entry.getPaymentMode() : "-", regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell("\u20B9 " + formatAmount(entry.getAmount()), regularFont, rowBg, TextAlignment.RIGHT));
                table.addCell(createDataCell("\u20B9 " + formatAmount(entry.getRunningTotal()), boldFont, rowBg, TextAlignment.RIGHT));

                alternate = !alternate;
            }

            // Total row
            Cell totalLabelCell = new Cell(1, 6)
                    .add(new Paragraph("TOTAL").setFont(boldFont).setFontSize(9)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(5);
            table.addCell(totalLabelCell);

            table.addCell(new Cell()
                    .add(new Paragraph("\u20B9 " + formatAmount(ledger.getTotalAmount()))
                            .setFont(boldFont).setFontSize(9)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setPadding(5));

            table.addCell(new Cell()
                    .add(new Paragraph("").setFont(boldFont).setFontSize(9))
                    .setBackgroundColor(HEADER_BG)
                    .setPadding(5));

            document.add(table);
        }

        // ===== FOOTER =====
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("This is a computer-generated document.")
                .setFont(italicFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));
        document.add(new Paragraph("Generated on: " + java.time.LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")))
                .setFont(italicFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        document.close();
        return baos.toByteArray();
    }

    private void addSocietyHeader(Document document, SocietySettings settings,
                                   PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph(settings.getSocietyName())
                .setFont(boldFont).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        document.add(new Paragraph("Reg. No: " + settings.getRegistrationNumber()
                + " | Reg. Date: " + settings.getRegistrationDate())
                .setFont(regularFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        String address = settings.getAddressLine1();
        if (settings.getAddressLine2() != null && !settings.getAddressLine2().isBlank()) {
            address += ", " + settings.getAddressLine2();
        }
        address += ", " + settings.getCity() + " - " + settings.getPincode()
                + ", " + settings.getState();
        document.add(new Paragraph(address)
                .setFont(regularFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));

        // Horizontal line
        Table line = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        line.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(HEADER_BG, 2))
                .setHeight(1));
        document.add(line);
    }

    private Cell createDataCell(String text, PdfFont font, DeviceRgb bgColor, TextAlignment alignment) {
        Cell cell = new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8)
                        .setTextAlignment(alignment))
                .setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        return cell;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "-";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        String amountStr = amount.setScale(2).toPlainString();
        String[] parts = amountStr.split("\\.");
        String intPart = parts[0];
        String decPart = parts.length > 1 ? parts[1] : "00";

        StringBuilder formatted = new StringBuilder();
        int len = intPart.length();
        if (len <= 3) {
            formatted.append(intPart);
        } else {
            formatted.append(intPart.substring(len - 3));
            int remaining = len - 3;
            String prefix = intPart.substring(0, remaining);
            StringBuilder prefixFormatted = new StringBuilder();
            for (int i = prefix.length() - 1, count = 0; i >= 0; i--, count++) {
                if (count > 0 && count % 2 == 0) {
                    prefixFormatted.insert(0, ",");
                }
                prefixFormatted.insert(0, prefix.charAt(i));
            }
            formatted.insert(0, ",");
            formatted.insert(0, prefixFormatted);
        }
        formatted.append(".").append(decPart);
        return formatted.toString();
    }
}
