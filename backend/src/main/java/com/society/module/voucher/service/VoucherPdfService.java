package com.society.module.voucher.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.society.exception.ResourceNotFoundException;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.entity.VoucherDocument;
import com.society.module.voucher.repository.VoucherDocumentRepository;
import com.society.module.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherPdfService {

    private final VoucherRepository voucherRepository;
    private final VoucherDocumentRepository voucherDocumentRepository;
    private final SocietySettingsService settingsService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb LIGHT_GRAY_BG = new DeviceRgb(245, 245, 245);

    // Backward-compatible overload
    public byte[] generateVoucherPdf(Long voucherId) throws IOException {
        return generateVoucherPdf(voucherId, false);
    }

    public byte[] generateVoucherPdf(Long voucherId, boolean includeBills) throws IOException {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "voucherId", voucherId));

        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(30, 40, 30, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // ===== SOCIETY HEADER =====
        addSocietyHeader(document, settings, boldFont, regularFont);

        // ===== VOUCHER TITLE =====
        addVoucherTitle(document, voucher, boldFont);

        // ===== VOUCHER INFO TABLE =====
        addVoucherInfoSection(document, voucher, boldFont, regularFont);

        // ===== PAYMENT DETAILS TABLE =====
        addPaymentDetailsSection(document, voucher, boldFont, regularFont);

        // ===== AMOUNT IN WORDS =====
        document.add(new Paragraph("\n"));
        Table amountTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        amountTable.addCell(createCell("Amount in Words: " + convertToWords(voucher.getAmount()),
                boldFont, 10, TextAlignment.LEFT).setBackgroundColor(LIGHT_GRAY_BG).setPadding(8));
        document.add(amountTable);

        // ===== NARRATION =====
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Narration / Description:")
                .setFont(boldFont).setFontSize(10));
        document.add(new Paragraph(voucher.getDescription())
                .setFont(regularFont).setFontSize(10)
                .setBorderBottom(new SolidBorder(0.5f))
                .setPaddingBottom(5));

        // ===== SIGNATURE SECTION =====
        document.add(new Paragraph("\n\n\n"));
        addSignatureSection(document, settings, boldFont, regularFont);

        // ===== FOOTER =====
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("This is a computer-generated voucher.")
                .setFont(italicFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));
        document.add(new Paragraph("Printed on: " + java.time.LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")))
                .setFont(italicFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        // ===== WATERMARK =====
        addWatermarkToAllPages(pdfDoc, settings);

        document.close();

        // If includeBills is true, append attached documents (PDFs/images) after the voucher
        if (includeBills) {
            return appendBillDocuments(baos.toByteArray(), voucher.getVoucherId());
        }

        return baos.toByteArray();
    }

    // Backward-compatible overload
    public byte[] generateBulkVoucherPdf(java.time.LocalDate startDate, java.time.LocalDate endDate,
                                          String financialYear, String type, String status) throws IOException {
        return generateBulkVoucherPdf(startDate, endDate, financialYear, type, status, false);
    }

    /**
     * Generate a single PDF containing all vouchers within a date range.
     * Each voucher starts on a new page. Includes a summary cover page.
     * Supports filtering by type and status.
     * When includeBills=true, attached documents are appended after each voucher in sequence.
     */
    public byte[] generateBulkVoucherPdf(java.time.LocalDate startDate, java.time.LocalDate endDate,
                                          String financialYear, String type, String status,
                                          boolean includeBills) throws IOException {
        List<Voucher> vouchers;

        if (financialYear != null && !financialYear.isBlank()) {
            vouchers = voucherRepository.findByFinancialYearOrderByVoucherDateAsc(financialYear);
        } else if (startDate != null && endDate != null) {
            vouchers = voucherRepository.findByVoucherDateBetweenOrderByVoucherDateAsc(startDate, endDate);
        } else {
            throw new com.society.exception.BusinessException("Either date range or financial year is required");
        }

        // Apply type filter
        if (type != null && !type.isBlank()) {
            com.society.enums.VoucherType voucherType = com.society.enums.VoucherType.valueOf(type);
            vouchers = vouchers.stream().filter(v -> v.getVoucherType() == voucherType).toList();
        }

        // Apply status filter
        if (status != null && !status.isBlank()) {
            com.society.enums.VoucherStatus voucherStatus = com.society.enums.VoucherStatus.valueOf(status);
            vouchers = vouchers.stream().filter(v -> v.getStatus() == voucherStatus).toList();
        }

        if (vouchers.isEmpty()) {
            throw new com.society.exception.BusinessException("No vouchers found for the given criteria");
        }

        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(30, 40, 30, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // ===== COVER PAGE / SUMMARY =====
        addSocietyHeader(document, settings, boldFont, regularFont);

        document.add(new Paragraph("\n"));
        document.add(new Paragraph("VOUCHER REGISTER")
                .setFont(boldFont).setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(10));

        String period;
        if (financialYear != null && !financialYear.isBlank()) {
            period = "Financial Year: " + financialYear;
        } else {
            period = "Period: " + startDate.format(DATE_FORMAT) + " to " + endDate.format(DATE_FORMAT);
        }
        // Add filter info
        if (type != null && !type.isBlank()) {
            period += " | Type: " + type;
        }
        if (status != null && !status.isBlank()) {
            period += " | Status: " + status;
        }
        document.add(new Paragraph(period)
                .setFont(regularFont).setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        // Summary counts
        long paymentCount = vouchers.stream().filter(v -> v.getVoucherType() == com.society.enums.VoucherType.PAYMENT).count();
        long receiptCount = vouchers.stream().filter(v -> v.getVoucherType() == com.society.enums.VoucherType.RECEIPT).count();
        long journalCount = vouchers.stream().filter(v -> v.getVoucherType() == com.society.enums.VoucherType.JOURNAL).count();
        long contraCount = vouchers.stream().filter(v -> v.getVoucherType() == com.society.enums.VoucherType.CONTRA).count();
        BigDecimal totalPayments = vouchers.stream()
                .filter(v -> v.getVoucherType() == com.society.enums.VoucherType.PAYMENT && v.getStatus() != com.society.enums.VoucherStatus.CANCELLED)
                .map(Voucher::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceipts = vouchers.stream()
                .filter(v -> v.getVoucherType() == com.society.enums.VoucherType.RECEIPT && v.getStatus() != com.society.enums.VoucherStatus.CANCELLED)
                .map(Voucher::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Summary table
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2}))
                .setWidth(UnitValue.createPercentValue(80))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);

        summaryTable.addHeaderCell(new Cell().add(new Paragraph("Type").setFont(boldFont).setFontSize(10))
                .setBackgroundColor(HEADER_BG).setFontColor(ColorConstants.WHITE).setPadding(6));
        summaryTable.addHeaderCell(new Cell().add(new Paragraph("Count").setFont(boldFont).setFontSize(10))
                .setBackgroundColor(HEADER_BG).setFontColor(ColorConstants.WHITE).setPadding(6).setTextAlignment(TextAlignment.CENTER));
        summaryTable.addHeaderCell(new Cell().add(new Paragraph("Total Amount").setFont(boldFont).setFontSize(10))
                .setBackgroundColor(HEADER_BG).setFontColor(ColorConstants.WHITE).setPadding(6).setTextAlignment(TextAlignment.RIGHT));

        addSummaryRow(summaryTable, "Payment Vouchers", paymentCount, totalPayments, regularFont);
        addSummaryRow(summaryTable, "Receipt Vouchers", receiptCount, totalReceipts, regularFont);
        addSummaryRow(summaryTable, "Journal Vouchers", journalCount, BigDecimal.ZERO, regularFont);
        addSummaryRow(summaryTable, "Contra Vouchers", contraCount, BigDecimal.ZERO, regularFont);

        // Total row
        summaryTable.addCell(new Cell().add(new Paragraph("TOTAL").setFont(boldFont).setFontSize(10))
                .setBackgroundColor(LIGHT_GRAY_BG).setPadding(6));
        summaryTable.addCell(new Cell().add(new Paragraph(String.valueOf(vouchers.size())).setFont(boldFont).setFontSize(10))
                .setBackgroundColor(LIGHT_GRAY_BG).setPadding(6).setTextAlignment(TextAlignment.CENTER));
        summaryTable.addCell(new Cell().add(new Paragraph("").setFont(boldFont).setFontSize(10))
                .setBackgroundColor(LIGHT_GRAY_BG).setPadding(6));

        document.add(summaryTable);

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Total Vouchers: " + vouchers.size())
                .setFont(regularFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + java.time.LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")))
                .setFont(italicFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        // ===== INDIVIDUAL VOUCHER PAGES =====
        for (Voucher voucher : vouchers) {
            document.add(new com.itextpdf.layout.element.AreaBreak());

            addSocietyHeader(document, settings, boldFont, regularFont);
            addVoucherTitle(document, voucher, boldFont);
            addVoucherInfoSection(document, voucher, boldFont, regularFont);
            addPaymentDetailsSection(document, voucher, boldFont, regularFont);

            // Amount in words
            document.add(new Paragraph("\n"));
            Table amountTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100));
            amountTable.addCell(createCell("Amount in Words: " + convertToWords(voucher.getAmount()),
                    boldFont, 10, TextAlignment.LEFT).setBackgroundColor(LIGHT_GRAY_BG).setPadding(8));
            document.add(amountTable);

            // Narration
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Narration / Description:")
                    .setFont(boldFont).setFontSize(10));
            document.add(new Paragraph(voucher.getDescription())
                    .setFont(regularFont).setFontSize(10)
                    .setBorderBottom(new SolidBorder(0.5f))
                    .setPaddingBottom(5));

            // Signatures
            document.add(new Paragraph("\n\n\n"));
            addSignatureSection(document, settings, boldFont, regularFont);

            // Footer
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("This is a computer-generated voucher.")
                    .setFont(italicFont).setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            // If includeBills, append attached documents inline (images on new pages)
            if (includeBills) {
                appendBillDocumentsInline(pdfDoc, document, voucher.getVoucherId());
            }
        }

        // ===== WATERMARK =====
        addWatermarkToAllPages(pdfDoc, settings);

        document.close();

        // If includeBills, merge any attached PDFs after the main document per voucher
        if (includeBills) {
            return mergeAttachedPdfs(baos.toByteArray(), vouchers);
        }

        return baos.toByteArray();
    }

    private void addSummaryRow(Table table, String type, long count, BigDecimal amount, PdfFont font) {
        table.addCell(new Cell().add(new Paragraph(type).setFont(font).setFontSize(9)).setPadding(5));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(font).setFontSize(9))
                .setPadding(5).setTextAlignment(TextAlignment.CENTER));
        table.addCell(new Cell().add(new Paragraph("₹ " + formatAmount(amount)).setFont(font).setFontSize(9))
                .setPadding(5).setTextAlignment(TextAlignment.RIGHT));
    }

    private void addSocietyHeader(Document document, SocietySettings settings,
                                   PdfFont boldFont, PdfFont regularFont) {
        // Society Name
        document.add(new Paragraph(settings.getSocietyName())
                .setFont(boldFont).setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        // Registration Number
        document.add(new Paragraph("Reg. No: " + settings.getRegistrationNumber()
                + " | Reg. Date: " + settings.getRegistrationDate())
                .setFont(regularFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        // Address
        String address = settings.getAddressLine1();
        if (settings.getAddressLine2() != null && !settings.getAddressLine2().isBlank()) {
            address += ", " + settings.getAddressLine2();
        }
        address += ", " + settings.getCity() + " - " + settings.getPincode()
                + ", " + settings.getState();
        document.add(new Paragraph(address)
                .setFont(regularFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        // Contact
        document.add(new Paragraph("Phone: " + settings.getPhone() + " | Email: " + settings.getEmail())
                .setFont(regularFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5));

        // Horizontal line
        Table line = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        line.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(HEADER_BG, 2))
                .setHeight(1));
        document.add(line);
    }

    private void addVoucherTitle(Document document, Voucher voucher, PdfFont boldFont) {
        String title = switch (voucher.getVoucherType()) {
            case PAYMENT -> "PAYMENT VOUCHER";
            case RECEIPT -> "RECEIPT VOUCHER";
            case JOURNAL -> "JOURNAL VOUCHER";
            case CONTRA -> "CONTRA VOUCHER";
        };

        document.add(new Paragraph("\n"));
        Table titleTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        titleTable.addCell(new Cell()
                .add(new Paragraph(title).setFont(boldFont).setFontSize(14)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(HEADER_BG)
                .setBorder(Border.NO_BORDER)
                .setPadding(8));
        document.add(titleTable);
        document.add(new Paragraph("\n"));
    }

    private void addVoucherInfoSection(Document document, Voucher voucher,
                                        PdfFont boldFont, PdfFont regularFont) {
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1.5f, 1, 1.5f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Row 1: Voucher Number + Date
        infoTable.addCell(createLabelCell("Voucher No:", boldFont));
        infoTable.addCell(createValueCell(voucher.getVoucherNumber(), regularFont));
        infoTable.addCell(createLabelCell("Date:", boldFont));
        infoTable.addCell(createValueCell(voucher.getVoucherDate().format(DATE_FORMAT), regularFont));

        // Row 2: Voucher Type + Financial Year
        infoTable.addCell(createLabelCell("Type:", boldFont));
        infoTable.addCell(createValueCell(voucher.getVoucherType().name(), regularFont));
        infoTable.addCell(createLabelCell("Financial Year:", boldFont));
        infoTable.addCell(createValueCell(voucher.getFinancialYear(), regularFont));

        // Row 3: Category + Status
        infoTable.addCell(createLabelCell("Category:", boldFont));
        infoTable.addCell(createValueCell(voucher.getCategory().replace('_', ' '), regularFont));
        infoTable.addCell(createLabelCell("Status:", boldFont));
        infoTable.addCell(createValueCell(voucher.getStatus().name(), regularFont));

        // Row 4: Vendor Name (full row)
        infoTable.addCell(createLabelCell("Paid To / Vendor:", boldFont));
        String vendorName = voucher.getVendor() != null ? voucher.getVendor().getVendorName() : "N/A";
        Cell vendorCell = new Cell(1, 3)
                .add(new Paragraph(vendorName).setFont(regularFont).setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setPadding(4)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
        infoTable.addCell(vendorCell);

        document.add(infoTable);
    }

    private void addPaymentDetailsSection(Document document, Voucher voucher,
                                           PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Payment Details")
                .setFont(boldFont).setFontSize(11)
                .setBorderBottom(new SolidBorder(0.5f))
                .setPaddingBottom(3));

        Table payTable = new Table(UnitValue.createPercentArray(new float[]{1, 1.5f, 1, 1.5f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Row 1: Amount + Payment Mode
        payTable.addCell(createLabelCell("Amount (Rs):", boldFont));
        payTable.addCell(createValueCell("₹ " + formatAmount(voucher.getAmount()), boldFont)
                .setFontSize(12));
        payTable.addCell(createLabelCell("Payment Mode:", boldFont));
        payTable.addCell(createValueCell(
                voucher.getPaymentMode() != null ? voucher.getPaymentMode().name() : "N/A", regularFont));

        // Row 2: Reference Number + Bill Number
        payTable.addCell(createLabelCell("Cheque/Txn Ref:", boldFont));
        payTable.addCell(createValueCell(
                voucher.getReferenceNumber() != null ? voucher.getReferenceNumber() : "N/A", regularFont));
        payTable.addCell(createLabelCell("Bill/Invoice No:", boldFont));
        payTable.addCell(createValueCell(
                voucher.getBillInvoiceNumber() != null ? voucher.getBillInvoiceNumber() : "N/A", regularFont));

        // Row 3: Bill Date
        payTable.addCell(createLabelCell("Bill Date:", boldFont));
        payTable.addCell(createValueCell(
                voucher.getBillDate() != null ? voucher.getBillDate().format(DATE_FORMAT) : "N/A", regularFont));
        payTable.addCell(createLabelCell("Created By:", boldFont));
        payTable.addCell(createValueCell(
                voucher.getCreatedBy() != null ? voucher.getCreatedBy() : "SYSTEM", regularFont));

        document.add(payTable);
    }

    private void addSignatureSection(Document document, SocietySettings settings,
                                      PdfFont boldFont, PdfFont regularFont) {
        Table sigTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        // Signature lines
        sigTable.addCell(createSignatureCell("Approved By", settings.getChairmanName(),
                "Chairman", boldFont, regularFont));
        sigTable.addCell(createSignatureCell("Verified By", settings.getSecretaryName(),
                "Secretary", boldFont, regularFont));
        sigTable.addCell(createSignatureCell("Prepared By", settings.getTreasurerName(),
                "Treasurer", boldFont, regularFont));

        document.add(sigTable);
    }

    private Cell createSignatureCell(String label, String name, String designation,
                                      PdfFont boldFont, PdfFont regularFont) {
        Cell cell = new Cell().setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(10);

        cell.add(new Paragraph("___________________")
                .setFont(regularFont).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER));
        cell.add(new Paragraph(name)
                .setFont(boldFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3));
        cell.add(new Paragraph("(" + designation + ")")
                .setFont(regularFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER));
        cell.add(new Paragraph(label)
                .setFont(regularFont).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        return cell;
    }

    // ===== HELPER METHODS =====

    private Cell createLabelCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setPadding(4)
                .setBackgroundColor(LIGHT_GRAY_BG)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell createValueCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setPadding(4)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private Cell createCell(String text, PdfFont font, int fontSize, TextAlignment alignment) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(fontSize)
                        .setTextAlignment(alignment))
                .setBorder(Border.NO_BORDER);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        // Indian format: 1,23,456.00
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

    private String convertToWords(BigDecimal amount) {
        if (amount == null) return "Zero Rupees Only";

        long rupees = amount.longValue();
        int paise = amount.subtract(new BigDecimal(rupees)).multiply(new BigDecimal(100)).intValue();

        String result = numberToWords(rupees) + " Rupees";
        if (paise > 0) {
            result += " and " + numberToWords(paise) + " Paise";
        }
        result += " Only";
        return result;
    }

    private String numberToWords(long number) {
        if (number == 0) return "Zero";

        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
                "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy",
                "Eighty", "Ninety"};

        if (number < 20) return ones[(int) number];
        if (number < 100) return tens[(int) number / 10] + (number % 10 != 0 ? " " + ones[(int) number % 10] : "");
        if (number < 1000) return ones[(int) number / 100] + " Hundred" +
                (number % 100 != 0 ? " and " + numberToWords(number % 100) : "");
        if (number < 100000) return numberToWords(number / 1000) + " Thousand" +
                (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        if (number < 10000000) return numberToWords(number / 100000) + " Lakh" +
                (number % 100000 != 0 ? " " + numberToWords(number % 100000) : "");
        return numberToWords(number / 10000000) + " Crore" +
                (number % 10000000 != 0 ? " " + numberToWords(number % 10000000) : "");
    }

    // ===== BILL/DOCUMENT APPENDING METHODS =====

    /**
     * Appends attached bill documents (PDFs and images) to a single voucher PDF.
     * PDFs are merged page-by-page. Images are added as full-page images.
     */
    private byte[] appendBillDocuments(byte[] voucherPdfBytes, Long voucherId) throws IOException {
        List<VoucherDocument> documents = voucherDocumentRepository
                .findByVoucher_VoucherIdOrderByUploadedOnDesc(voucherId);

        if (documents.isEmpty()) {
            return voucherPdfBytes;
        }

        ByteArrayOutputStream mergedBaos = new ByteArrayOutputStream();
        PdfDocument mergedDoc = new PdfDocument(new PdfWriter(mergedBaos));
        PdfMerger merger = new PdfMerger(mergedDoc);

        // First, add the voucher PDF pages
        PdfDocument voucherDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(voucherPdfBytes)));
        merger.merge(voucherDoc, 1, voucherDoc.getNumberOfPages());
        voucherDoc.close();

        // Then append each attached document
        for (VoucherDocument doc : documents) {
            try {
                Path filePath = Paths.get(uploadDir, doc.getFilePath());
                if (!Files.exists(filePath)) {
                    log.warn("Attached document not found on disk: {}", filePath);
                    continue;
                }

                String fileName = doc.getDocumentName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    // Merge PDF pages
                    PdfDocument attachedPdf = new PdfDocument(new PdfReader(filePath.toString()));
                    merger.merge(attachedPdf, 1, attachedPdf.getNumberOfPages());
                    attachedPdf.close();
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) {
                    // Add image as a full page
                    addImageAsPage(mergedDoc, filePath);
                }
                // Skip unsupported formats (doc, docx, etc.)
            } catch (Exception e) {
                log.warn("Failed to append document '{}' to PDF: {}", doc.getDocumentName(), e.getMessage());
            }
        }

        mergedDoc.close();
        return mergedBaos.toByteArray();
    }

    /**
     * Appends image documents inline within the currently open PDF document (for bulk generation).
     * PDF attachments are handled separately via mergeAttachedPdfs after document.close().
     */
    private void appendBillDocumentsInline(PdfDocument pdfDoc, Document document, Long voucherId) {
        List<VoucherDocument> documents = voucherDocumentRepository
                .findByVoucher_VoucherIdOrderByUploadedOnDesc(voucherId);

        for (VoucherDocument doc : documents) {
            try {
                Path filePath = Paths.get(uploadDir, doc.getFilePath());
                if (!Files.exists(filePath)) {
                    continue;
                }

                String fileName = doc.getDocumentName().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) {
                    // Add image on a new page within the same document flow
                    document.add(new AreaBreak());
                    Image img = new Image(ImageDataFactory.create(filePath.toString()));
                    img.setAutoScale(true);
                    img.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    document.add(img);
                }
                // PDF attachments are handled by mergeAttachedPdfs after document.close()
            } catch (Exception e) {
                log.warn("Failed to add inline image for voucher {}: {}", voucherId, e.getMessage());
            }
        }
    }

    /**
     * After the bulk PDF is generated, merge any attached PDF documents into the output.
     * For bulk mode: voucher page → its images (inline) → its PDF attachments (merged after).
     */
    private byte[] mergeAttachedPdfs(byte[] mainPdfBytes, List<Voucher> vouchers) throws IOException {
        // Check if any voucher has PDF attachments
        boolean hasPdfAttachments = false;
        for (Voucher v : vouchers) {
            List<VoucherDocument> docs = voucherDocumentRepository
                    .findByVoucher_VoucherIdOrderByUploadedOnDesc(v.getVoucherId());
            for (VoucherDocument doc : docs) {
                if (doc.getDocumentName().toLowerCase().endsWith(".pdf")) {
                    Path filePath = Paths.get(uploadDir, doc.getFilePath());
                    if (Files.exists(filePath)) {
                        hasPdfAttachments = true;
                        break;
                    }
                }
            }
            if (hasPdfAttachments) break;
        }

        if (!hasPdfAttachments) {
            return mainPdfBytes;
        }

        // We need to rebuild: for each voucher's pages, insert its PDF attachments after
        // Simple approach: since images are already inline, just append all PDF attachments at the end
        // grouped by voucher for reference
        ByteArrayOutputStream mergedBaos = new ByteArrayOutputStream();
        PdfDocument mergedDoc = new PdfDocument(new PdfWriter(mergedBaos));
        PdfMerger merger = new PdfMerger(mergedDoc);

        // Add main bulk PDF
        PdfDocument mainDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(mainPdfBytes)));
        merger.merge(mainDoc, 1, mainDoc.getNumberOfPages());
        mainDoc.close();

        // Append PDF attachments per voucher in sequence
        for (Voucher v : vouchers) {
            List<VoucherDocument> docs = voucherDocumentRepository
                    .findByVoucher_VoucherIdOrderByUploadedOnDesc(v.getVoucherId());
            for (VoucherDocument doc : docs) {
                try {
                    if (!doc.getDocumentName().toLowerCase().endsWith(".pdf")) continue;
                    Path filePath = Paths.get(uploadDir, doc.getFilePath());
                    if (!Files.exists(filePath)) continue;

                    PdfDocument attachedPdf = new PdfDocument(new PdfReader(filePath.toString()));
                    merger.merge(attachedPdf, 1, attachedPdf.getNumberOfPages());
                    attachedPdf.close();
                } catch (Exception e) {
                    log.warn("Failed to merge PDF attachment '{}' for voucher {}: {}",
                            doc.getDocumentName(), v.getVoucherNumber(), e.getMessage());
                }
            }
        }

        mergedDoc.close();
        return mergedBaos.toByteArray();
    }

    /**
     * Adds an image file as a full A4 page in the PDF document.
     */
    private void addImageAsPage(PdfDocument pdfDoc, Path imagePath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(imagePath);
        Image img = new Image(ImageDataFactory.create(imageBytes));

        // Create a new page and add the image scaled to fit
        pdfDoc.addNewPage(PageSize.A4);
        Document tempDoc = new Document(pdfDoc, PageSize.A4, false);
        tempDoc.setMargins(20, 20, 20, 20);

        float pageWidth = PageSize.A4.getWidth() - 40;
        float pageHeight = PageSize.A4.getHeight() - 40;

        // Scale image to fit within page bounds
        float imgWidth = img.getImageWidth();
        float imgHeight = img.getImageHeight();
        float scale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight);
        img.setWidth(imgWidth * scale);
        img.setHorizontalAlignment(HorizontalAlignment.CENTER);

        // Position on the last page
        int lastPage = pdfDoc.getNumberOfPages();
        img.setFixedPosition(lastPage, 20, PageSize.A4.getHeight() - 20 - (imgHeight * scale));

        tempDoc.add(img);
        tempDoc.flush();
    }

    // ===== WATERMARK =====

    /**
     * Adds a diagonal watermark with society name and registration number on all pages.
     * The watermark is semi-transparent light gray text drawn behind content.
     */
    private void addWatermarkToAllPages(PdfDocument pdfDoc, SocietySettings settings) {
        try {
            String watermarkText = settings.getSocietyName() + " | Reg. No: " + settings.getRegistrationNumber();
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            PdfExtGState gs = new PdfExtGState().setFillOpacity(0.06f);

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                float pageWidth = page.getPageSize().getWidth();
                float pageHeight = page.getPageSize().getHeight();

                PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);
                canvas.saveState();
                canvas.setExtGState(gs);
                canvas.beginText();
                canvas.setFontAndSize(font, 28);
                canvas.setFillColor(new DeviceRgb(150, 150, 150));

                // Position at center of page, rotated 45 degrees diagonally
                float centerX = pageWidth / 2;
                float centerY = pageHeight / 2;
                double angle = Math.toRadians(45);

                canvas.setTextMatrix(
                        (float) Math.cos(angle), (float) Math.sin(angle),
                        (float) -Math.sin(angle), (float) Math.cos(angle),
                        centerX - 200, centerY - 50
                );
                canvas.showText(watermarkText);
                canvas.endText();
                canvas.restoreState();
            }
        } catch (Exception e) {
            log.warn("Failed to add watermark: {}", e.getMessage());
        }
    }
}
