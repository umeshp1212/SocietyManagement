package com.society.module.maintenance.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MaintenanceBillPdfService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb TABLE_HEADER_BG = new DeviceRgb(66, 66, 66);
    private static final DeviceRgb ARREARS_BG = new DeviceRgb(255, 248, 225);
    private static final DeviceRgb TOTAL_BG = new DeviceRgb(227, 242, 253);

    /**
     * Generate PDF for a single maintenance bill
     */
    public byte[] generateBillPdf(Long billId) throws IOException {
        MaintenanceBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));

        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(25, 30, 25, 30);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        addBillPage(document, bill, settings, boldFont, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate PDF for all bills of a given month (bulk download)
     */
    public byte[] generateBulkBillsPdf(int month, int year) throws IOException {
        List<MaintenanceBill> bills = billRepository.findByBillMonthAndBillYear(month, year);
        if (bills.isEmpty()) {
            throw new ResourceNotFoundException("No bills found for " + Month.of(month).name() + " " + year);
        }

        SocietySettings settings = settingsService.getSettings();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(25, 30, 25, 30);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        for (int i = 0; i < bills.size(); i++) {
            if (i > 0) {
                document.add(new AreaBreak());
            }
            addBillPage(document, bills.get(i), settings, boldFont, regularFont);
        }

        document.close();
        return baos.toByteArray();
    }

    private void addBillPage(Document document, MaintenanceBill bill, SocietySettings settings,
                             PdfFont boldFont, PdfFont regularFont) {

        // ===== SOCIETY HEADER =====
        addSocietyHeader(document, settings, boldFont, regularFont);

        // ===== BILL TITLE =====
        String period = Month.of(bill.getBillMonth()).name() + " " + bill.getBillYear();
        document.add(new Paragraph("MAINTENANCE BILL - " + period)
                .setFont(boldFont).setFontSize(13)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8).setMarginBottom(8));

        // ===== UNIT & OWNER INFO =====
        addUnitInfoSection(document, bill, boldFont, regularFont);

        // ===== CHARGES BREAKUP TABLE =====
        addChargesTable(document, bill, boldFont, regularFont);

        // ===== LAST MONTH PAYMENT RECEIPT =====
        addLastMonthReceipt(document, bill, boldFont, regularFont);

        // ===== FOOTER WITH INSTRUCTIONS & SIGNATURES =====
        addBillFooter(document, settings, boldFont, regularFont);
    }

    private void addSocietyHeader(Document document, SocietySettings settings,
                                   PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph(settings.getSocietyName())
                .setFont(boldFont).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        String address = "";
        if (settings.getAddressLine1() != null) address += settings.getAddressLine1();
        if (settings.getAddressLine2() != null) address += ", " + settings.getAddressLine2();
        if (settings.getCity() != null) address += ", " + settings.getCity();
        if (settings.getPincode() != null) address += " - " + settings.getPincode();

        document.add(new Paragraph(address)
                .setFont(regularFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        if (settings.getRegistrationNumber() != null) {
            document.add(new Paragraph("Reg. No: " + settings.getRegistrationNumber())
                    .setFont(regularFont).setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(4));
        }

        // Divider line
        Table divider = new Table(1).setWidth(UnitValue.createPercentValue(100));
        divider.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(1))
                .setHeight(1));
        document.add(divider);
    }

    private void addUnitInfoSection(Document document, MaintenanceBill bill,
                                     PdfFont boldFont, PdfFont regularFont) {
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8).setMarginBottom(8);

        addInfoCell(infoTable, "Unit No:", bill.getUnit().getUnitNumber(), boldFont, regularFont);
        addInfoCell(infoTable, "Bill Date:", bill.getBillDate().format(DATE_FORMAT), boldFont, regularFont);
        addInfoCell(infoTable, "Owner:", bill.getUnit().getOwnerNames() != null
                ? bill.getUnit().getOwnerNames() : "-", boldFont, regularFont);
        addInfoCell(infoTable, "Due Date:", bill.getDueDate().format(DATE_FORMAT), boldFont, regularFont);
        addInfoCell(infoTable, "Area (sq.ft):", bill.getUnitAreaSqft() != null
                && bill.getUnitAreaSqft().compareTo(BigDecimal.ZERO) > 0
                ? bill.getUnitAreaSqft().stripTrailingZeros().toPlainString() : "-", boldFont, regularFont);
        addInfoCell(infoTable, "Status:", bill.getStatus().name(), boldFont, regularFont);

        document.add(infoTable);
    }

    private void addInfoCell(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        table.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(label).setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(value).setFont(regularFont).setFontSize(9)));
    }

    private void addChargesTable(Document document, MaintenanceBill bill,
                                  PdfFont boldFont, PdfFont regularFont) {
        // Table: Sr | Description | Calculation | Amount
        Table table = new Table(UnitValue.createPercentArray(new float[]{0.5f, 3f, 2.5f, 1.5f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Header row
        addTableHeaderCell(table, "Sr.", boldFont);
        addTableHeaderCell(table, "Description", boldFont);
        addTableHeaderCell(table, "Calculation", boldFont);
        addTableHeaderCell(table, "Amount (Rs.)", boldFont);

        // Line items
        List<BillLineItem> lineItems = bill.getLineItems();
        int sr = 1;
        if (lineItems != null) {
            for (BillLineItem item : lineItems) {
                String calcText = "";
                if ("AREA_BASED".equals(item.getCalculationType()) && item.getAreaSqft() != null) {
                    calcText = item.getAreaSqft() + " sq.ft x Rs." + item.getRate();
                } else {
                    calcText = "Flat Charge";
                }

                addTableCell(table, String.valueOf(sr++), regularFont, TextAlignment.CENTER);
                addTableCell(table, item.getChargeName(), regularFont, TextAlignment.LEFT);
                addTableCell(table, calcText, regularFont, TextAlignment.LEFT);
                addTableCell(table, formatAmount(item.getAmount()), regularFont, TextAlignment.RIGHT);
            }
        }

        // Subtotal - Current Month Charges
        addTotalRow(table, "Current Month Charges", formatAmount(bill.getAmount()),
                boldFont, LIGHT_GRAY);

        // Arrears
        BigDecimal previousArrears = bill.getPreviousArrears() != null ? bill.getPreviousArrears() : BigDecimal.ZERO;
        BigDecimal interest = bill.getInterestOnArrears() != null ? bill.getInterestOnArrears() : BigDecimal.ZERO;

        if (previousArrears.compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "Previous Arrears (Principal)", formatAmount(previousArrears),
                    regularFont, ARREARS_BG);
        }
        if (interest.compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "Interest on Arrears @ 1% per month", formatAmount(interest),
                    regularFont, ARREARS_BG);
        }

        // Grand Total
        addTotalRow(table, "GRAND TOTAL", formatAmount(bill.getTotalAmount()),
                boldFont, TOTAL_BG);

        // Paid & Balance
        BigDecimal paidAmount = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "Amount Paid", "- " + formatAmount(paidAmount),
                    regularFont, new DeviceRgb(232, 245, 233));
        }
        addTotalRow(table, "BALANCE DUE", formatAmount(bill.getBalanceAmount()),
                boldFont, new DeviceRgb(255, 243, 224));

        document.add(table);
    }

    private void addTableHeaderCell(Table table, String text, PdfFont font) {
        table.addCell(new Cell()
                .setBackgroundColor(TABLE_HEADER_BG)
                .add(new Paragraph(text).setFont(font).setFontSize(9)
                        .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                .setPadding(5)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addTableCell(Table table, String text, PdfFont font, TextAlignment alignment) {
        table.addCell(new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9))
                .setPadding(4)
                .setTextAlignment(alignment));
    }

    private void addTotalRow(Table table, String label, String amount, PdfFont font, DeviceRgb bgColor) {
        Cell labelCell = new Cell(1, 3)
                .add(new Paragraph(label).setFont(font).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(bgColor)
                .setPadding(5);
        Cell amountCell = new Cell()
                .add(new Paragraph(amount).setFont(font).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(bgColor)
                .setPadding(5);
        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    /**
     * Add last month's payment receipt section if the previous bill was paid.
     */
    private void addLastMonthReceipt(Document document, MaintenanceBill bill,
                                      PdfFont boldFont, PdfFont regularFont) {
        // Find the previous month's bill for this unit
        int prevMonth = bill.getBillMonth() - 1;
        int prevYear = bill.getBillYear();
        if (prevMonth < 1) {
            prevMonth = 12;
            prevYear--;
        }

        Optional<MaintenanceBill> prevBillOpt = billRepository.findByUnit_UnitIdAndBillMonthAndBillYear(
                bill.getUnit().getUnitId(), prevMonth, prevYear);

        if (prevBillOpt.isEmpty()) {
            return; // No previous bill exists
        }

        MaintenanceBill prevBill = prevBillOpt.get();

        // Get payments for previous bill
        List<MaintenancePayment> prevPayments = paymentRepository.findByBill_BillIdOrderByPaymentDateDesc(
                prevBill.getBillId());

        if (prevPayments.isEmpty()) {
            return; // No payment was made for last month
        }

        // Add separator
        document.add(new Paragraph("\n"));
        Table divider = new Table(1).setWidth(UnitValue.createPercentValue(100));
        divider.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(new DeviceRgb(200, 200, 200), 1))
                .setHeight(1));
        document.add(divider);

        // Receipt header
        String prevPeriod = Month.of(prevMonth).name() + " " + prevYear;
        document.add(new Paragraph("PAYMENT RECEIPT - " + prevPeriod)
                .setFont(boldFont).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8).setMarginBottom(6));

        // Receipt table
        Table receiptTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 2f, 1.5f, 1.5f, 1.5f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Header
        String[] headers = {"Date", "Receipt No.", "Mode", "Amount", "Status"};
        for (String h : headers) {
            receiptTable.addCell(new Cell()
                    .setBackgroundColor(new DeviceRgb(76, 175, 80))
                    .add(new Paragraph(h).setFont(boldFont).setFontSize(8)
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                    .setPadding(4)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Payment rows
        for (MaintenancePayment payment : prevPayments) {
            addTableCell(receiptTable, payment.getPaymentDate() != null
                    ? payment.getPaymentDate().format(DATE_FORMAT) : "-", regularFont, TextAlignment.CENTER);
            addTableCell(receiptTable, payment.getReceiptNumber() != null
                    ? payment.getReceiptNumber() : "-", regularFont, TextAlignment.CENTER);
            addTableCell(receiptTable, payment.getPaymentMode().name(), regularFont, TextAlignment.CENTER);
            addTableCell(receiptTable, formatAmount(payment.getAmount()), regularFont, TextAlignment.RIGHT);
            addTableCell(receiptTable, payment.getStatus().name(), regularFont, TextAlignment.CENTER);
        }

        // Total paid for previous month
        BigDecimal totalPaid = prevPayments.stream()
                .map(MaintenancePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Cell totalLabel = new Cell(1, 3)
                .add(new Paragraph("Total Paid for " + prevPeriod).setFont(boldFont).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(new DeviceRgb(232, 245, 233))
                .setPadding(4);
        Cell totalAmt = new Cell(1, 2)
                .add(new Paragraph("Rs. " + formatAmount(totalPaid)).setFont(boldFont).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(new DeviceRgb(232, 245, 233))
                .setPadding(4);
        receiptTable.addCell(totalLabel);
        receiptTable.addCell(totalAmt);

        document.add(receiptTable);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }

    /**
     * Add footer with E.&.O.E., payment instructions, bank details, and signatures.
     */
    private void addBillFooter(Document document, SocietySettings settings,
                                PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("\n"));

        // E.&.O.E.
        document.add(new Paragraph("E.&.O.E.")
                .setFont(boldFont).setFontSize(8)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(4));

        // Payment instructions
        String instructions = "1) PLEASE PAY YOUR DUES ON OR BEFORE DUE DATE OTHERWISE INTEREST WILL BE CHARGED @ 1% P.M.\n"
                + "2) CHEQUE PAYMENT WILL BE ACCEPTED ON OR BEFORE 25th OF EVERY MONTH.\n"
                + "3) PAYMENT SHOULD BE MADE IN FAVOUR OF \"" + settings.getSocietyName().toUpperCase()
                + "\" OR SHOULD BE PAY BY NEFT.\n"
                + "4) SOCIETY BANK DETAIL - T.D.C.C. BANK., GAVTHAN AREA BRANCH, "
                + "SAVING A/C NO.011100310000070, IFSC CODE-TDCB0000111, "
                + "SOCIETY E-MAIL ID - " + (settings.getEmail() != null ? settings.getEmail() : "");

        document.add(new Paragraph(instructions)
                .setFont(regularFont).setFontSize(7.5f)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(16));

        // Society name line
        document.add(new Paragraph("For " + settings.getSocietyName().toUpperCase())
                .setFont(boldFont).setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(24));

        // Signatures row: Chairman | Secretary | Treasurer
        Table sigTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("________________").setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(9))
                .add(new Paragraph("CHAIRMAN").setTextAlignment(TextAlignment.CENTER)
                        .setFont(boldFont).setFontSize(8))
                .add(new Paragraph(settings.getChairmanName() != null ? settings.getChairmanName() : "")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(7)));

        sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("________________").setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(9))
                .add(new Paragraph("SECRETARY").setTextAlignment(TextAlignment.CENTER)
                        .setFont(boldFont).setFontSize(8))
                .add(new Paragraph(settings.getSecretaryName() != null ? settings.getSecretaryName() : "")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(7)));

        sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("________________").setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(9))
                .add(new Paragraph("TREASURER").setTextAlignment(TextAlignment.CENTER)
                        .setFont(boldFont).setFontSize(8))
                .add(new Paragraph(settings.getTreasurerName() != null ? settings.getTreasurerName() : "")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFont(regularFont).setFontSize(7)));

        document.add(sigTable);
    }
}
