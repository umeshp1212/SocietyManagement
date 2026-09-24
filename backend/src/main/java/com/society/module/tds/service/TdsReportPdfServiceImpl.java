package com.society.module.tds.service;

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
import com.society.common.PagedResponse;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenPDF/iText implementation of {@link TdsReportPdfService}, mirroring the layout and helpers of
 * {@code VendorLedgerPdfService}. The line set is fetched through {@link TdsService} in the same
 * deduction-date-descending order as the list endpoint, and the summary totals are reused from
 * {@link TdsService#summarize(TdsFilterRequest)} so the PDF matches the summary endpoint exactly.
 */
@Service
@RequiredArgsConstructor
public class TdsReportPdfServiceImpl implements TdsReportPdfService {

    private final TdsService tdsService;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb LIGHT_GRAY_BG = new DeviceRgb(245, 245, 245);

    /** Maximum page size accepted by the list endpoint (see {@link TdsService#listTdsLines}). */
    private static final int PAGE_SIZE = 200;
    /** Hard cap on the number of lines rendered, to bound the 10s generation budget (Req 9.3). */
    private static final int MAX_LINES = 5000;

    @Override
    public byte[] generateTdsReportPdf(TdsFilterRequest filter) throws IOException {
        List<TdsLineDTO> lines = fetchAllLines(filter);
        TdsSummaryDTO summary = tdsService.summarize(filter);
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

        // ===== REPORT TITLE =====
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("TDS DEDUCTION & REMITTANCE REPORT")
                .setFont(boldFont).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        // ===== SUMMARY =====
        document.add(new Paragraph("Total Deducted: \u20B9 " + formatAmount(summary.getTotalDeducted())
                + "  |  Paid to Accountant: \u20B9 " + formatAmount(summary.getTotalPaidToAccountant())
                + "  |  Paid to IT Dept: \u20B9 " + formatAmount(summary.getTotalPaidToItDept()))
                .setFont(boldFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));
        document.add(new Paragraph("Pending: \u20B9 " + formatAmount(summary.getTotalPending())
                + "  |  Line Count: " + summary.getLineCount())
                .setFont(boldFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(15));

        // ===== LINES TABLE =====
        if (lines.isEmpty()) {
            document.add(new Paragraph("No records matched the filter.")
                    .setFont(italicFont).setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20));
        } else {
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{1.5f, 3f, 1.2f, 1f, 1.5f, 1.5f, 1.8f, 1.8f}))
                    .setWidth(UnitValue.createPercentValue(100));

            String[] headers = {"Voucher No", "Vendor", "Section", "Rate (%)", "TDS Amount",
                    "Deduction Date", "Status", "Challan No"};
            for (String header : headers) {
                table.addHeaderCell(new Cell()
                        .add(new Paragraph(header).setFont(boldFont).setFontSize(8)
                                .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(HEADER_BG)
                        .setPadding(5)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE));
            }

            boolean alternate = false;
            for (TdsLineDTO line : lines) {
                DeviceRgb rowBg = alternate ? LIGHT_GRAY_BG : null;

                table.addCell(createDataCell(nullSafe(line.getVoucherNumber()), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(truncate(line.getVendorName(), 40), regularFont, rowBg, TextAlignment.LEFT));
                table.addCell(createDataCell(nullSafe(line.getTdsSection()), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(formatRate(line.getTdsRate()), regularFont, rowBg, TextAlignment.RIGHT));
                table.addCell(createDataCell("\u20B9 " + formatAmount(line.getTdsAmount()), regularFont, rowBg, TextAlignment.RIGHT));
                table.addCell(createDataCell(formatDate(line.getDeductionDate()), regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(line.getStatus() != null ? line.getStatus().name() : "-", regularFont, rowBg, TextAlignment.CENTER));
                table.addCell(createDataCell(nullSafe(line.getChallanNumber()), regularFont, rowBg, TextAlignment.CENTER));

                alternate = !alternate;
            }

            // Total row
            Cell totalLabelCell = new Cell(1, 4)
                    .add(new Paragraph("TOTAL").setFont(boldFont).setFontSize(9)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(5);
            table.addCell(totalLabelCell);

            table.addCell(new Cell()
                    .add(new Paragraph("\u20B9 " + formatAmount(summary.getTotalDeducted()))
                            .setFont(boldFont).setFontSize(9)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_BG)
                    .setPadding(5));

            table.addCell(new Cell(1, 3)
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

    /**
     * Fetches all matching lines in the list endpoint's deduction-date-descending order. The list
     * endpoint caps page size at {@link #PAGE_SIZE}, so the first page probes the total and any
     * further pages are accumulated into a single ordered list (capped at {@link #MAX_LINES}) to
     * keep generation within the 10s budget (Req 9.3).
     */
    private List<TdsLineDTO> fetchAllLines(TdsFilterRequest filter) {
        List<TdsLineDTO> all = new ArrayList<>();

        PagedResponse<TdsLineDTO> firstPage =
                tdsService.listTdsLines(filter, PageRequest.of(0, PAGE_SIZE));
        all.addAll(firstPage.getContent());

        int totalPages = firstPage.getTotalPages();
        for (int page = 1; page < totalPages && all.size() < MAX_LINES; page++) {
            PagedResponse<TdsLineDTO> next =
                    tdsService.listTdsLines(filter, PageRequest.of(page, PAGE_SIZE));
            all.addAll(next.getContent());
        }

        if (all.size() > MAX_LINES) {
            return new ArrayList<>(all.subList(0, MAX_LINES));
        }
        return all;
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

    private String nullSafe(String text) {
        return (text == null || text.isBlank()) ? "-" : text;
    }

    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "-";
    }

    private String formatRate(BigDecimal rate) {
        return rate != null ? rate.stripTrailingZeros().toPlainString() : "-";
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
