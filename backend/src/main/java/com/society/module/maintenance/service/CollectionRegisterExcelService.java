package com.society.module.maintenance.service;

import com.society.module.maintenance.entity.BillLineItem;
import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.maintenance.repository.MaintenanceBillRepository;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
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
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the manager's two monthly sheets as a single downloadable .xlsx workbook so the
 * manager need not fill them by hand before handing to the accountant:
 *
 * <ol>
 *   <li><b>Collection Sheet</b> — payment-focused: Bill No, Member, Balance, Total Maintenance,
 *       Total Amount, Date of Payment, Cheque No, Amount Received, Cash/Bank Name.</li>
 *   <li><b>Register Bill</b> — charge-breakup-focused: Bill No, Owner, each charge head
 *       (Service, Sinking, Water, Interest on Arrears, Non-Occupancy, Welfare, 2W/4W parking,
 *       penalties), Opening Amount, Received Amount, Balance, Total Maintenance, Total Amount.</li>
 * </ol>
 *
 * <p>Charge-head columns are summed from {@code bill_line_items} by their stable charge codes;
 * "Interest on Arrears" comes from the bill's interest fields; opening/received/balance come
 * from the bill and its realised (SUCCESS/VERIFIED) payments.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionRegisterExcelService {

    private final MaintenanceBillRepository billRepository;
    private final MaintenancePaymentRepository paymentRepository;
    private final SocietySettingsService settingsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] generate(int year, int month) throws IOException {
        List<MaintenanceBill> bills = billRepository.findByBillMonthAndBillYear(month, year);
        bills.sort(Comparator.comparing(b -> b.getUnit() != null ? b.getUnit().getUnitNumber() : ""));

        SocietySettings settings = safeSettings();
        String monthLabel = Month.of(month).name() + " " + year;

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            buildCollectionSheet(wb, s, settings, monthLabel, bills);
            buildRegisterSheet(wb, s, settings, monthLabel, bills);
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== Collection Sheet ====================

    private void buildCollectionSheet(XSSFWorkbook wb, Styles s, SocietySettings settings,
                                      String monthLabel, List<MaintenanceBill> bills) {
        Sheet sheet = wb.createSheet("Collection Sheet");
        String[] headers = {
                "Bill No", "Name of Member", "Balance Amount", "Total Maintenance", "Total Amount",
                "Date of Payment", "Cheque No", "Amount Received", "Cash / Bank Name"
        };
        int rowIdx = titleBlock(sheet, s, settings, "Collection Sheet - " + monthLabel, headers.length);
        rowIdx = headerRow(sheet, s, headers, rowIdx);

        BigDecimal totMaint = BigDecimal.ZERO, totTotal = BigDecimal.ZERO,
                totReceived = BigDecimal.ZERO, totBalance = BigDecimal.ZERO;

        for (MaintenanceBill b : bills) {
            List<MaintenancePayment> payments = realisedPayments(b);
            BigDecimal received = payments.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Date / cheque / mode: join across (usually one) realised payment(s).
            String payDates = joinNonBlank(payments.stream()
                    .map(p -> p.getPaymentDate() != null ? p.getPaymentDate().format(DATE_FMT) : null).toList());
            String chequeNos = joinNonBlank(payments.stream()
                    .map(p -> p.getPaymentMode() == MaintenancePayment.PaymentMode.CHEQUE ? p.getTransactionId() : null).toList());
            String modes = joinNonBlank(payments.stream()
                    .map(p -> p.getPaymentMode() != null ? p.getPaymentMode().name() : null).toList());

            BigDecimal totalMaintenance = nz(b.getAmount());         // current month charges
            BigDecimal totalAmount = nz(b.getTotalAmount());         // grand total (incl arrears etc.)
            BigDecimal balance = nz(b.getBalanceAmount());

            Row r = sheet.createRow(rowIdx++);
            int c = 0;
            textCell(r, c++, s, String.valueOf(b.getBillId()));
            textCell(r, c++, s, memberName(b));
            amountCell(r, c++, s, balance);
            amountCell(r, c++, s, totalMaintenance);
            amountCell(r, c++, s, totalAmount);
            textCell(r, c++, s, payDates);
            textCell(r, c++, s, chequeNos);
            amountCell(r, c++, s, received);
            textCell(r, c++, s, modes);

            totMaint = totMaint.add(totalMaintenance);
            totTotal = totTotal.add(totalAmount);
            totReceived = totReceived.add(received);
            totBalance = totBalance.add(balance);
        }

        // Totals row
        Row tr = sheet.createRow(rowIdx++);
        totalLabelCell(tr, 0, s, "TOTAL");
        amountTotalCell(tr, 2, s, totBalance);
        amountTotalCell(tr, 3, s, totMaint);
        amountTotalCell(tr, 4, s, totTotal);
        amountTotalCell(tr, 7, s, totReceived);

        autosize(sheet, headers.length);
    }

    // ==================== Register Bill ====================

    private void buildRegisterSheet(XSSFWorkbook wb, Styles s, SocietySettings settings,
                                    String monthLabel, List<MaintenanceBill> bills) {
        Sheet sheet = wb.createSheet("Register Bill");
        String[] headers = {
                "Bill No", "Name of Owner", "Service Charges", "Sinking Fund", "Repair Fund", "Water Charges",
                "Interest on Arrears", "Non-Occupancy Charges", "Welfare Fund",
                "2 Wheeler Parking", "4 Wheeler Parking", "Penalty - Littering", "Penalty - Wrong Parking",
                "Opening Amount", "Received Amount", "Balance Amount", "Total Maintenance", "Total Amount"
        };
        int rowIdx = titleBlock(sheet, s, settings, "Register Bill - " + monthLabel, headers.length);
        rowIdx = headerRow(sheet, s, headers, rowIdx);

        for (MaintenanceBill b : bills) {
            List<BillLineItem> items = b.getLineItems() != null ? b.getLineItems() : List.of();

            BigDecimal service = lineAmount(items, "SERVICE", "MAINTENANCE");
            BigDecimal sinking = lineAmount(items, "SINKING_FUND");
            BigDecimal repair = lineAmount(items, "REPAIR_FUND");
            BigDecimal water = lineAmount(items, "WATER_CHARGES");
            BigDecimal noc = lineAmount(items, "NOC_CHARGES");
            BigDecimal welfare = lineAmount(items, "WELFARE_FUND");
            BigDecimal twoW = lineAmount(items, "PARKING_2W");
            BigDecimal fourW = lineAmount(items, "PARKING_4W");
            BigDecimal penLitter = lineAmount(items, "PENALTY_WATER_WASTAGE", "PENALTY_LITTERING");
            BigDecimal penParking = lineAmount(items, "PENALTY_WRONG_PARKING");

            // Interest on arrears = this month's new interest + carried interest (total interest).
            BigDecimal interest = nz(b.getInterestArrears());
            if (interest.signum() == 0) interest = nz(b.getInterestOnArrears());

            // Opening amount = previous balance carried in (principal + carried interest).
            BigDecimal carriedInterest = nz(b.getInterestArrears()).subtract(nz(b.getNewInterest())).max(BigDecimal.ZERO);
            BigDecimal opening = nz(b.getPrincipalArrears()).add(carriedInterest);

            BigDecimal received = realisedPayments(b).stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = nz(b.getBalanceAmount());
            BigDecimal totalMaintenance = nz(b.getAmount());
            BigDecimal totalAmount = nz(b.getTotalAmount());

            Row r = sheet.createRow(rowIdx++);
            int c = 0;
            textCell(r, c++, s, String.valueOf(b.getBillId()));
            textCell(r, c++, s, memberName(b));
            amountCell(r, c++, s, service);
            amountCell(r, c++, s, sinking);
            amountCell(r, c++, s, repair);
            amountCell(r, c++, s, water);
            amountCell(r, c++, s, interest);
            amountCell(r, c++, s, noc);
            amountCell(r, c++, s, welfare);
            amountCell(r, c++, s, twoW);
            amountCell(r, c++, s, fourW);
            amountCell(r, c++, s, penLitter);
            amountCell(r, c++, s, penParking);
            amountCell(r, c++, s, opening);
            amountCell(r, c++, s, received);
            amountCell(r, c++, s, balance);
            amountCell(r, c++, s, totalMaintenance);
            amountCell(r, c++, s, totalAmount);
        }

        autosize(sheet, headers.length);
    }

    // ==================== Helpers ====================

    private List<MaintenancePayment> realisedPayments(MaintenanceBill b) {
        List<MaintenancePayment> out = new ArrayList<>();
        for (MaintenancePayment p : paymentRepository.findByBill_BillId(b.getBillId())) {
            if (p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.VERIFIED) {
                out.add(p);
            }
        }
        return out;
    }

    /** Sum the amounts of all line items whose charge code matches any of the given codes. */
    private BigDecimal lineAmount(List<BillLineItem> items, String... codes) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BillLineItem li : items) {
            String code = li.getChargeCode();
            if (code == null) continue;
            for (String want : codes) {
                if (code.equalsIgnoreCase(want)) {
                    sum = sum.add(li.getAmount() != null ? li.getAmount() : BigDecimal.ZERO);
                    break;
                }
            }
        }
        return sum;
    }

    private String memberName(MaintenanceBill b) {
        if (b.getUnit() == null) return "";
        String owners = b.getUnit().getOwnerNames();
        String unitNo = b.getUnit().getUnitNumber();
        return (unitNo != null ? unitNo : "") + (owners != null && !owners.isBlank() ? " - " + owners : "");
    }

    private String joinNonBlank(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private SocietySettings safeSettings() {
        try {
            SocietySettings s = settingsService.getSettings();
            return s != null ? s : new SocietySettings();
        } catch (Exception e) {
            return new SocietySettings();
        }
    }

    // ---- POI layout helpers ----

    private int titleBlock(Sheet sheet, Styles s, SocietySettings settings, String title, int cols) {
        int r = 0;
        Row r0 = sheet.createRow(r++);
        Cell c0 = r0.createCell(0);
        c0.setCellValue(settings.getSocietyName() != null ? settings.getSocietyName() : "Society");
        c0.setCellStyle(s.societyName);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));

        Row r1 = sheet.createRow(r++);
        Cell c1 = r1.createCell(0);
        c1.setCellValue(title);
        c1.setCellStyle(s.docTitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));

        r++; // blank spacer row
        return r;
    }

    private int headerRow(Sheet sheet, Styles s, String[] headers, int rowIdx) {
        Row hr = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = hr.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(i >= 2 ? s.tableHeaderRight : s.tableHeader);
        }
        return rowIdx;
    }

    private void textCell(Row r, int c, Styles s, String value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(s.text);
    }

    private void amountCell(Row r, int c, Styles s, BigDecimal value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(s.amount);
    }

    private void totalLabelCell(Row r, int c, Styles s, String value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(value);
        cell.setCellStyle(s.totalLabel);
    }

    private void amountTotalCell(Row r, int c, Styles s, BigDecimal value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(s.totalAmount);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(w + 600, 12000));
        }
    }

    /** Pre-built cell styles for one workbook. */
    private static final class Styles {
        final CellStyle societyName;
        final CellStyle docTitle;
        final CellStyle tableHeader;
        final CellStyle tableHeaderRight;
        final CellStyle text;
        final CellStyle amount;
        final CellStyle totalLabel;
        final CellStyle totalAmount;

        Styles(Workbook wb) {
            DataFormat df = wb.createDataFormat();
            short inr = df.getFormat("#,##0.00");

            Font societyFont = wb.createFont();
            societyFont.setBold(true);
            societyFont.setFontHeightInPoints((short) 14);

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            societyName = center(wb, societyFont);
            docTitle = center(wb, titleFont);

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

            amount = wb.createCellStyle();
            box(amount);
            amount.setDataFormat(inr);
            amount.setAlignment(HorizontalAlignment.RIGHT);

            totalLabel = wb.createCellStyle();
            box(totalLabel);
            totalLabel.setFont(boldFont);
            totalLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabel.setAlignment(HorizontalAlignment.RIGHT);

            totalAmount = wb.createCellStyle();
            totalAmount.cloneStyleFrom(amount);
            totalAmount.setFont(boldFont);
            totalAmount.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalAmount.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private static CellStyle center(Workbook wb, Font font) {
            CellStyle st = wb.createCellStyle();
            st.setFont(font);
            st.setAlignment(HorizontalAlignment.CENTER);
            return st;
        }

        private static void box(CellStyle st) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
    }
}
