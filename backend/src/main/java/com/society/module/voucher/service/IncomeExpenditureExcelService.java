package com.society.module.voucher.service;

import com.society.enums.VoucherStatus;
import com.society.enums.VoucherType;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Builds the monthly Income &amp; Expenditure statement as a downloadable .xlsx workbook
 * for the managing committee meeting.
 *
 * <p>Expenditure is sourced from FINAL (fully approved / paid) {@code PAYMENT} vouchers whose
 * {@code voucherDate} falls in the selected month. Income is sourced from realised maintenance
 * receipts (status SUCCESS or VERIFIED) by {@code paymentDate}. The income section is empty
 * until the online maintenance module goes live, after which it totals automatically.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncomeExpenditureExcelService {

    private final VoucherRepository voucherRepository;
    private final MaintenancePaymentRepository maintenancePaymentRepository;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * @param year  four-digit year, e.g. 2026
     * @param month 1-12
     */
    public byte[] generateMonthlyStatement(int year, int month) throws IOException {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        SocietySettings settings = safeSettings();
        String monthLabel = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;

        // ---- Expenditure: FINAL + PAYMENT vouchers in the month ----
        List<Voucher> expenses = voucherRepository
                .findByVoucherDateBetweenOrderByVoucherDateAsc(start, end)
                .stream()
                .filter(v -> v.getVoucherType() == VoucherType.PAYMENT)
                .filter(v -> v.getStatus() == VoucherStatus.FINAL)
                .toList();

        // ---- Income: realised maintenance receipts in the month (empty until module is live) ----
        List<MaintenancePayment> income = maintenancePaymentRepository.findIncomeBetween(
                List.of(PaymentStatus.SUCCESS, PaymentStatus.VERIFIED), start, end);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            BigDecimal totalIncome = income.stream()
                    .map(MaintenancePayment::getAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalExpense = expenses.stream()
                    .map(Voucher::getAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            buildSummarySheet(wb, styles, settings, monthLabel, totalIncome, totalExpense);
            buildExpenditureSheet(wb, styles, expenses, totalExpense);
            buildIncomeSheet(wb, styles, income, totalIncome);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private SocietySettings safeSettings() {
        try {
            return settingsService.getSettings();
        } catch (Exception e) {
            log.warn("Could not load society settings for I&E statement header: {}", e.getMessage());
            return null;
        }
    }

    private String societyName(SocietySettings settings) {
        return settings != null && settings.getSocietyName() != null ? settings.getSocietyName() : "Society Management";
    }

    // ==================== Summary sheet ====================

    private void buildSummarySheet(XSSFWorkbook wb, Styles s, SocietySettings settings, String monthLabel,
                                   BigDecimal totalIncome, BigDecimal totalExpense) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 22 * 256);

        int r = 0;
        r = titleBlock(sheet, s, societyName(settings),
                "Income & Expenditure Statement", monthLabel, r, 0, 1);

        r++; // blank

        Row head = sheet.createRow(r++);
        cell(head, 0, "Particulars", s.tableHeader);
        cell(head, 1, "Amount (INR)", s.tableHeaderRight);

        BigDecimal net = totalIncome.subtract(totalExpense);

        Row ri = sheet.createRow(r++);
        cell(ri, 0, "Total Income", s.labelBold);
        amountCell(ri, 1, totalIncome, s.amount);

        Row re = sheet.createRow(r++);
        cell(re, 0, "Total Expenditure", s.labelBold);
        amountCell(re, 1, totalExpense, s.amount);

        Row rn = sheet.createRow(r++);
        cell(rn, 0, net.signum() >= 0 ? "Net Surplus" : "Net Deficit", s.totalLabel);
        amountCell(rn, 1, net, s.totalAmount);

        r++;
        Row note = sheet.createRow(r++);
        cell(note, 0, "Expenditure = approved (FINAL) payment vouchers for the month.", s.note);
        Row note2 = sheet.createRow(r++);
        cell(note2, 0, "Income = realised maintenance receipts (blank until the online module is live).", s.note);
        Row gen = sheet.createRow(r);
        cell(gen, 0, "Generated on: " + LocalDate.now().format(DATE_FMT), s.note);
    }

    // ==================== Expenditure sheet ====================

    private void buildExpenditureSheet(XSSFWorkbook wb, Styles s, List<Voucher> expenses, BigDecimal totalExpense) {
        Sheet sheet = wb.createSheet("Expenditure");
        String[] headers = {
                "Date", "Voucher No", "Category", "Vendor Name", "Pay To", "Description / Purpose",
                "Amount (INR)", "TDS (INR)", "Net Payable (INR)", "Payment Mode", "Cheque / Ref No"
        };
        int[] widths = {12, 14, 18, 26, 26, 40, 15, 12, 16, 14, 18};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

        int r = 0;
        Row header = sheet.createRow(r++);
        for (int i = 0; i < headers.length; i++) {
            boolean numeric = i >= 6 && i <= 8;
            cell(header, i, headers[i], numeric ? s.tableHeaderRight : s.tableHeader);
        }

        for (Voucher v : expenses) {
            Row row = sheet.createRow(r++);
            cell(row, 0, v.getVoucherDate() != null ? v.getVoucherDate().format(DATE_FMT) : "", s.text);
            cell(row, 1, nz(v.getVoucherNumber()), s.text);
            cell(row, 2, v.getCategory() != null ? v.getCategory().replace('_', ' ') : "", s.text);
            cell(row, 3, v.getVendor() != null ? nz(v.getVendor().getVendorName()) : "", s.text);
            cell(row, 4, nz(v.getPayToName()), s.text);
            cell(row, 5, nz(v.getDescription()), s.text);
            amountCell(row, 6, v.getAmount(), s.amount);
            amountCell(row, 7, v.getTdsAmount(), s.amount);
            amountCell(row, 8, v.getNetPayable() != null ? v.getNetPayable() : v.getAmount(), s.amount);
            cell(row, 9, v.getPaymentMode() != null ? v.getPaymentMode().name() : "", s.text);
            cell(row, 10, nz(v.getReferenceNumber()), s.text);
        }

        if (expenses.isEmpty()) {
            Row empty = sheet.createRow(r++);
            cell(empty, 0, "No approved expenses for this month.", s.note);
        }

        Row totalRow = sheet.createRow(r);
        cell(totalRow, 5, "TOTAL EXPENDITURE", s.totalLabel);
        amountCell(totalRow, 6, totalExpense, s.totalAmount);
    }

    // ==================== Income sheet ====================

    private void buildIncomeSheet(XSSFWorkbook wb, Styles s, List<MaintenancePayment> income, BigDecimal totalIncome) {
        Sheet sheet = wb.createSheet("Income");
        String[] headers = {"Date", "Receipt No", "Payer", "Payment Mode", "Transaction / Ref", "Amount (INR)"};
        int[] widths = {12, 16, 28, 16, 24, 15};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);

        int r = 0;
        Row header = sheet.createRow(r++);
        for (int i = 0; i < headers.length; i++) {
            cell(header, i, headers[i], i == 5 ? s.tableHeaderRight : s.tableHeader);
        }

        for (MaintenancePayment p : income) {
            Row row = sheet.createRow(r++);
            cell(row, 0, p.getPaymentDate() != null ? p.getPaymentDate().format(DATE_FMT) : "", s.text);
            cell(row, 1, nz(p.getReceiptNumber()), s.text);
            cell(row, 2, nz(p.getPayerName()), s.text);
            cell(row, 3, p.getPaymentMode() != null ? p.getPaymentMode().name() : "", s.text);
            cell(row, 4, p.getTransactionId() != null ? p.getTransactionId() : nz(p.getReceiptNumber()), s.text);
            amountCell(row, 5, p.getAmount(), s.amount);
        }

        if (income.isEmpty()) {
            Row empty = sheet.createRow(r++);
            cell(empty, 0, "No maintenance income recorded for this month (online module not live yet).", s.note);
        }

        Row totalRow = sheet.createRow(r);
        cell(totalRow, 4, "TOTAL INCOME", s.totalLabel);
        amountCell(totalRow, 5, totalIncome, s.totalAmount);
    }

    // ==================== helpers ====================

    private int titleBlock(Sheet sheet, Styles s, String society, String title, String subtitle,
                           int startRow, int firstCol, int lastCol) {
        int r = startRow;
        Row r1 = sheet.createRow(r++);
        cell(r1, firstCol, society, s.societyName);
        sheet.addMergedRegion(new CellRangeAddress(r1.getRowNum(), r1.getRowNum(), firstCol, lastCol));

        Row r2 = sheet.createRow(r++);
        cell(r2, firstCol, title, s.docTitle);
        sheet.addMergedRegion(new CellRangeAddress(r2.getRowNum(), r2.getRowNum(), firstCol, lastCol));

        Row r3 = sheet.createRow(r++);
        cell(r3, firstCol, "For the month of " + subtitle, s.docSubtitle);
        sheet.addMergedRegion(new CellRangeAddress(r3.getRowNum(), r3.getRowNum(), firstCol, lastCol));
        return r;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void amountCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0d);
        if (style != null) c.setCellStyle(style);
    }

    /** Pre-built cell styles for one workbook. */
    private static final class Styles {
        final CellStyle societyName;
        final CellStyle docTitle;
        final CellStyle docSubtitle;
        final CellStyle tableHeader;
        final CellStyle tableHeaderRight;
        final CellStyle text;
        final CellStyle amount;
        final CellStyle labelBold;
        final CellStyle totalLabel;
        final CellStyle totalAmount;
        final CellStyle note;

        Styles(Workbook wb) {
            DataFormat df = wb.createDataFormat();
            short inr = df.getFormat("#,##0.00");

            Font societyFont = wb.createFont();
            societyFont.setBold(true);
            societyFont.setFontHeightInPoints((short) 14);

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            Font subtitleFont = wb.createFont();
            subtitleFont.setItalic(true);
            subtitleFont.setFontHeightInPoints((short) 10);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            Font noteFont = wb.createFont();
            noteFont.setItalic(true);
            noteFont.setFontHeightInPoints((short) 9);
            noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            societyName = center(wb, societyFont);
            docTitle = center(wb, titleFont);
            docSubtitle = center(wb, subtitleFont);

            tableHeader = wb.createCellStyle();
            tableHeader.setFont(headerFont);
            tableHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            box(tableHeader);
            tableHeader.setAlignment(HorizontalAlignment.LEFT);

            tableHeaderRight = wb.createCellStyle();
            tableHeaderRight.cloneStyleFrom(tableHeader);
            tableHeaderRight.setAlignment(HorizontalAlignment.RIGHT);

            text = wb.createCellStyle();
            box(text);
            text.setWrapText(true);
            text.setVerticalAlignment(VerticalAlignment.TOP);

            amount = wb.createCellStyle();
            box(amount);
            amount.setDataFormat(inr);
            amount.setAlignment(HorizontalAlignment.RIGHT);

            labelBold = wb.createCellStyle();
            box(labelBold);
            labelBold.setFont(boldFont);

            totalLabel = wb.createCellStyle();
            totalLabel.cloneStyleFrom(labelBold);
            totalLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabel.setAlignment(HorizontalAlignment.RIGHT);

            totalAmount = wb.createCellStyle();
            totalAmount.cloneStyleFrom(amount);
            totalAmount.setFont(boldFont);
            totalAmount.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalAmount.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            note = wb.createCellStyle();
            note.setFont(noteFont);
        }

        private static CellStyle center(Workbook wb, Font font) {
            CellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(HorizontalAlignment.CENTER);
            return s;
        }

        private static void box(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
