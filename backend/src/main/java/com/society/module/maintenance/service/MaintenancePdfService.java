package com.society.module.maintenance.service;

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
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.entity.BillLineItem;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenancePdfService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb GREEN = new DeviceRgb(46, 125, 50);
    private static final DeviceRgb RED = new DeviceRgb(198, 40, 40);
    private static final DeviceRgb ORANGE = new DeviceRgb(230, 81, 0);

    // ======================== BILL PDF ========================

    public byte[] generateBillPdf(Long billId) throws IOException {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", "billId", billId));

        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(30, 40, 30, 40);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // Society Header
        addSocietyHeader(doc, settings, bold, regular);

        // Bill Title
        String period = Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear();
        Paragraph title = new Paragraph("MAINTENANCE BILL")
                .setFont(bold).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10).setMarginBottom(2);
        doc.add(title);
        doc.add(new Paragraph(period)
                .setFont(bold).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(HEADER_BG).setMarginBottom(12));

        // Bill Info
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        infoTable.addCell(labelCell("Unit:", bold));
        infoTable.addCell(valueCell(bill.getUnit().getUnitNumber(), regular));
        infoTable.addCell(labelCell("Bill Date:", bold));
        infoTable.addCell(valueCell(bill.getBillDate().format(DATE_FMT), regular));

        infoTable.addCell(labelCell("Owner:", bold));
        infoTable.addCell(valueCell(bill.getUnit().getOwnerNames() != null ? bill.getUnit().getOwnerNames() : "-", regular));
        infoTable.addCell(labelCell("Due Date:", bold));
        infoTable.addCell(valueCell(bill.getDueDate().format(DATE_FMT), regular));

        if (bill.getUnitAreaSqft() != null) {
            infoTable.addCell(labelCell("Area (Sq.ft):", bold));
            infoTable.addCell(valueCell(bill.getUnitAreaSqft().toPlainString(), regular));
            infoTable.addCell(labelCell("Status:", bold));
            Cell statusCell = valueCell(bill.getStatus().name(), bold);
            switch (bill.getStatus()) {
                case PAID -> statusCell.setFontColor(GREEN);
                case PARTIALLY_PAID -> statusCell.setFontColor(ORANGE);
                default -> statusCell.setFontColor(RED);
            }
            infoTable.addCell(statusCell);
        }
        doc.add(infoTable);

        doc.add(new Paragraph("\n"));

        // Line Items Table
        doc.add(new Paragraph("Charges Breakdown").setFont(bold).setFontSize(10).setMarginBottom(4));

        Table chargesTable = new Table(UnitValue.createPercentArray(new float[]{1, 4, 2, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        addTableHeader(chargesTable, bold, "#", "Description", "Rate", "Amount");

        List<BillLineItem> items = bill.getLineItems();
        int idx = 1;
        if (items != null && !items.isEmpty()) {
            for (BillLineItem item : items) {
                String rateStr = "";
                if (item.getRate() != null) {
                    rateStr = "PER_SQFT".equals(item.getCalculationType())
                            ? "₹" + item.getRate() + "/sqft"
                            : "₹" + item.getRate() + " flat";
                }
                addTableRow(chargesTable, regular, String.valueOf(idx++), item.getChargeName(),
                        rateStr, "₹ " + item.getAmount().toPlainString());
            }
        }

        // Subtotal row
        addTotalRow(chargesTable, bold, "Current Month Charges", "₹ " + format(bill.getAmount()));
        doc.add(chargesTable);

        doc.add(new Paragraph("\n"));

        // Arrears & Interest
        BigDecimal arrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal interest = bill.getInterestOnArrears() != null ? bill.getInterestOnArrears() : BigDecimal.ZERO;

        if (arrears.compareTo(BigDecimal.ZERO) > 0 || interest.compareTo(BigDecimal.ZERO) > 0) {
            Table arrearsTable = new Table(UnitValue.createPercentArray(new float[]{5, 2}))
                    .setWidth(UnitValue.createPercentValue(100));

            addTableHeader(arrearsTable, bold, "Description", "Amount");

            if (arrears.compareTo(BigDecimal.ZERO) > 0) {
                addTableRow(arrearsTable, regular, "Principal Outstanding (Arrears)", "₹ " + format(arrears));
            }
            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                addTableRow(arrearsTable, regular, "Interest on Arrears", "₹ " + format(interest));
            }
            doc.add(arrearsTable);
        }

        doc.add(new Paragraph("\n"));

        // Grand Total Summary
        Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{5, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        addSummaryRow(summaryTable, bold, "Total Bill Amount", "₹ " + format(bill.getTotalAmount()), HEADER_BG);

        BigDecimal paid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            addSummaryRow(summaryTable, bold, "Amount Paid", "- ₹ " + format(paid), GREEN);
        }

        BigDecimal balance = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : bill.getTotalAmount();
        DeviceRgb balColor = balance.compareTo(BigDecimal.ZERO) <= 0 ? GREEN : RED;
        addSummaryRow(summaryTable, bold, "Balance Due", "₹ " + format(balance), balColor);

        doc.add(summaryTable);

        // Footer
        doc.add(new Paragraph("\n\n"));
        doc.add(new Paragraph("This is a computer-generated bill and does not require a signature.")
                .setFont(italic).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));
        doc.add(new Paragraph("Generated on: " + LocalDateTime.now().format(DATETIME_FMT))
                .setFont(italic).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        doc.close();
        return baos.toByteArray();
    }

    // ======================== RECEIPT PDF ========================

    public byte[] generateReceiptPdf(Long paymentId) throws IOException {
        MaintenancePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "paymentId", paymentId));

        MaintenanceBill bill = payment.getBill();
        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc, PageSize.A4);
        doc.setMargins(30, 40, 30, 40);

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italic = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        // Society Header
        addSocietyHeader(doc, settings, bold, regular);

        // Receipt Title
        boolean isPartial = bill.getBalanceAmount() != null && bill.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0;
        String titleText = isPartial ? "PAYMENT RECEIPT (PARTIAL)" : "PAYMENT RECEIPT";
        Paragraph title = new Paragraph(titleText)
                .setFont(bold).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10).setMarginBottom(12);
        if (isPartial) {
            title.setFontColor(ORANGE);
        } else {
            title.setFontColor(GREEN);
        }
        doc.add(title);

        // Receipt Info
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        infoTable.addCell(labelCell("Receipt No:", bold));
        infoTable.addCell(valueCell(payment.getReceiptNumber() != null ? payment.getReceiptNumber() : "-", regular));
        infoTable.addCell(labelCell("Payment Date:", bold));
        infoTable.addCell(valueCell(payment.getPaymentDate().format(DATE_FMT), regular));

        infoTable.addCell(labelCell("Received From:", bold));
        infoTable.addCell(valueCell(payment.getPayerName() != null ? payment.getPayerName() : "-", regular));
        infoTable.addCell(labelCell("Payment Mode:", bold));
        infoTable.addCell(valueCell(formatPaymentMode(payment.getPaymentMode().name()), regular));

        infoTable.addCell(labelCell("Unit:", bold));
        infoTable.addCell(valueCell(payment.getUnit().getUnitNumber(), regular));
        infoTable.addCell(labelCell("Bill Period:", bold));
        String billPeriod = Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear();
        infoTable.addCell(valueCell(billPeriod, regular));

        if (payment.getRazorpayPaymentId() != null && !payment.getRazorpayPaymentId().isBlank()) {
            infoTable.addCell(labelCell("Transaction ID:", bold));
            infoTable.addCell(valueCell(payment.getRazorpayPaymentId(), regular));
            infoTable.addCell(new Cell().setBorder(Border.NO_BORDER));
            infoTable.addCell(new Cell().setBorder(Border.NO_BORDER));
        } else if (payment.getTransactionId() != null && !payment.getTransactionId().isBlank()) {
            infoTable.addCell(labelCell("Transaction ID:", bold));
            infoTable.addCell(valueCell(payment.getTransactionId(), regular));
            infoTable.addCell(new Cell().setBorder(Border.NO_BORDER));
            infoTable.addCell(new Cell().setBorder(Border.NO_BORDER));
        }

        doc.add(infoTable);

        doc.add(new Paragraph("\n"));

        // Payment Details
        Table detailTable = new Table(UnitValue.createPercentArray(new float[]{5, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        addTableHeader(detailTable, bold, "Description", "Amount");
        addTableRow(detailTable, regular, "Total Bill Amount (" + billPeriod + ")", "₹ " + format(bill.getTotalAmount()));

        // Show discount info if applicable
        boolean hasDiscount = payment.getDiscountAmount() != null
                && payment.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
        if (hasDiscount) {
            addTableRow(detailTable, regular, "Original Amount", "₹ " + format(payment.getOriginalAmount()));
            addTableRow(detailTable, regular,
                    "Online Payment Discount (" + format(payment.getDiscountPercent()) + "%)",
                    "- ₹ " + format(payment.getDiscountAmount()));
        }

        addTableRow(detailTable, regular, "Amount Paid (This Payment)", "₹ " + format(payment.getAmount()));

        // Show cumulative paid and remaining balance
        BigDecimal totalPaid = bill.getPaidAmount() != null ? bill.getPaidAmount() : payment.getAmount();
        BigDecimal remaining = bill.getBalanceAmount() != null ? bill.getBalanceAmount() : BigDecimal.ZERO;

        addTableRow(detailTable, regular, "Total Paid Against This Bill", "₹ " + format(totalPaid));
        addTableRow(detailTable, regular, "Remaining Balance", "₹ " + format(remaining));

        doc.add(detailTable);

        doc.add(new Paragraph("\n"));

        // Amount Box
        Table amountBox = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        Cell amountCell = new Cell()
                .add(new Paragraph("Amount Received: ₹ " + format(payment.getAmount()))
                        .setFont(bold).setFontSize(14).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(LIGHT_BG)
                .setPadding(12)
                .setBorder(new SolidBorder(HEADER_BG, 1));
        amountBox.addCell(amountCell);
        doc.add(amountBox);

        if (isPartial) {
            doc.add(new Paragraph("\n"));
            doc.add(new Paragraph("Note: This is a partial payment. Outstanding balance of ₹ " + format(remaining)
                    + " is pending against " + billPeriod + " bill.")
                    .setFont(bold).setFontSize(9).setFontColor(ORANGE)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Signature section
        doc.add(new Paragraph("\n\n\n"));
        Table sigTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("_______________________").setTextAlignment(TextAlignment.CENTER).setFont(regular).setFontSize(9))
                .add(new Paragraph("Treasurer").setTextAlignment(TextAlignment.CENTER).setFont(bold).setFontSize(9)));
        sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("_______________________").setTextAlignment(TextAlignment.CENTER).setFont(regular).setFontSize(9))
                .add(new Paragraph("Secretary").setTextAlignment(TextAlignment.CENTER).setFont(bold).setFontSize(9)));
        doc.add(sigTable);

        // Footer
        doc.add(new Paragraph("\n"));
        doc.add(new Paragraph("This is a computer-generated receipt and does not require a signature.")
                .setFont(italic).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));
        doc.add(new Paragraph("Generated on: " + LocalDateTime.now().format(DATETIME_FMT))
                .setFont(italic).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY));

        doc.close();
        return baos.toByteArray();
    }

    // ======================== HELPERS ========================

    private void addSocietyHeader(Document doc, SocietySettings settings, PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);

        Cell headerCell = new Cell().setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingBottom(8)
                .setBorderBottom(new SolidBorder(HEADER_BG, 2));

        headerCell.add(new Paragraph(settings.getSocietyName() != null ? settings.getSocietyName() : "Society Management")
                .setFont(bold).setFontSize(16).setFontColor(HEADER_BG));

        String address = buildAddress(settings);
        if (!address.isEmpty()) {
            headerCell.add(new Paragraph(address)
                    .setFont(regular).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY));
        }

        if (settings.getRegistrationNumber() != null && !settings.getRegistrationNumber().isBlank()) {
            headerCell.add(new Paragraph("Reg. No: " + settings.getRegistrationNumber())
                    .setFont(regular).setFontSize(8).setFontColor(ColorConstants.GRAY));
        }

        header.addCell(headerCell);
        doc.add(header);
    }

    private String buildAddress(SocietySettings s) {
        StringBuilder sb = new StringBuilder();
        if (s.getAddressLine1() != null && !s.getAddressLine1().isBlank()) sb.append(s.getAddressLine1());
        if (s.getAddressLine2() != null && !s.getAddressLine2().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.getAddressLine2());
        }
        if (s.getCity() != null && !s.getCity().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.getCity());
        }
        if (s.getState() != null && !s.getState().isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.getState());
        }
        if (s.getPincode() != null && !s.getPincode().isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(s.getPincode());
        }
        return sb.toString();
    }

    private Cell labelCell(String text, PdfFont font) {
        return new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(text).setFont(font).setFontSize(9))
                .setPadding(4)
                .setBackgroundColor(LIGHT_BG);
    }

    private Cell valueCell(String text, PdfFont font) {
        return new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(text).setFont(font).setFontSize(9))
                .setPadding(4)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
    }

    private void addTableHeader(Table table, PdfFont bold, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(HEADER_BG).setPadding(6));
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values) {
        for (String v : values) {
            table.addCell(new Cell()
                    .add(new Paragraph(v).setFont(font).setFontSize(9))
                    .setPadding(5)
                    .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                    .setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER).setBorderTop(Border.NO_BORDER));
        }
    }

    private void addTotalRow(Table table, PdfFont bold, String label, String value) {
        int cols = table.getNumberOfColumns();
        Cell labelCell = new Cell(1, cols - 1)
                .add(new Paragraph(label).setFont(bold).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(LIGHT_BG).setPadding(6).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(ColorConstants.GRAY, 1));
        Cell valueCell = new Cell()
                .add(new Paragraph(value).setFont(bold).setFontSize(10))
                .setBackgroundColor(LIGHT_BG).setPadding(6).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(ColorConstants.GRAY, 1));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addSummaryRow(Table table, PdfFont bold, String label, String value, DeviceRgb valueColor) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(10))
                .setPadding(8).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)));
        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(bold).setFontSize(10).setFontColor(valueColor))
                .setPadding(8).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)));
    }

    private String format(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPaymentMode(String mode) {
        if (mode == null) return "-";
        return switch (mode) {
            case "RAZORPAY" -> "Online (Razorpay)";
            case "UPI" -> "UPI";
            case "GPAY" -> "Google Pay";
            case "PHONEPE" -> "PhonePe";
            case "NEFT" -> "NEFT";
            case "RTGS" -> "RTGS";
            case "IMPS" -> "IMPS";
            case "CHEQUE" -> "Cheque";
            case "CASH" -> "Cash";
            case "BANK_TRANSFER" -> "Bank Transfer";
            default -> mode;
        };
    }
}
