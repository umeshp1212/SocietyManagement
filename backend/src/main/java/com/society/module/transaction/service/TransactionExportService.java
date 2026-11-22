package com.society.module.transaction.service;

import com.society.exception.BusinessException;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.repository.MaintenancePaymentRepository;
import com.society.module.maintenance.service.CashfreeService;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Cashfree settlement report for the accountant. For a settlement-date range it pulls the
 * settlement reconciliation from Cashfree (which payments were paid out in which bank
 * settlement), matches each payment to the unit/payer in our system and produces:
 * <ol>
 *   <li><b>Settlements</b> - one row per settlement cycle (UTR): date, gross, charges,
 *       settlement amount, no. of payments and the units involved.</li>
 *   <li><b>Settlement Details</b> - one row per payment inside each settlement: who paid what.</li>
 *   <li><b>Unit-wise</b> - total per unit across the period.</li>
 * </ol>
 * Runs in a read-only transaction because the payment's unit is lazily loaded.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionExportService {

    private final CashfreeService cashfreeService;
    private final MaintenancePaymentRepository paymentRepository;
    private final SocietySettingsService settingsService;

    /** One Cashfree reconciliation row matched to our payment (if any). */
    private static final class Line {
        String utr = "";
        String settlementDate = "";
        String eventType = "";
        String orderId = "";
        String cfPaymentId = "";
        BigDecimal eventAmount = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal cycleAmount;           // settlement-level amount when Cashfree supplies it
        MaintenancePayment payment;       // null when not found in our system
    }

    public byte[] generate(LocalDate start, LocalDate end, Authentication auth) throws IOException {
        if (start == null || end == null) {
            throw new BusinessException("Both start date and end date are required for the report");
        }
        if (start.isAfter(end)) {
            throw new BusinessException("Invalid date range: start date " + start + " is after end date " + end);
        }
        // Access is controlled by the TRANSACTION_EXPORT permission on the controller.

        List<Line> lines = new ArrayList<>();
        for (Map<String, Object> row : cashfreeService.fetchSettlementRecon(start, end)) {
            lines.add(toLine(row));
        }
        lines.sort(Comparator.comparing((Line l) -> l.settlementDate).thenComparing(l -> l.utr)
                .thenComparing(l -> unitNo(l.payment)));

        // Group by settlement cycle, preserving order.
        Map<String, List<Line>> cycles = new LinkedHashMap<>();
        for (Line l : lines) {
            cycles.computeIfAbsent(l.utr.isEmpty() ? l.settlementDate : l.utr, k -> new ArrayList<>()).add(l);
        }

        SocietySettings settings = safeSettings();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            buildSettlements(wb, s, settings, start, end, cycles);
            buildDetails(wb, s, settings, start, end, lines);
            buildUnitWise(wb, s, settings, start, end, lines);
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== Mapping ====================

    private Line toLine(Map<String, Object> row) {
        Line l = new Line();
        l.utr = str(row, "settlement_utr", "utr");
        l.settlementDate = dateOnly(str(row, "settlement_date", "event_settlement_date", "event_time"));
        l.eventType = str(row, "event_type");
        l.orderId = str(row, "order_id");
        l.cfPaymentId = str(row, "cf_payment_id", "payment_id");
        l.eventAmount = dec(row, "event_amount", "amount");
        BigDecimal tax = dec(row, "service_tax");
        l.charges = dec(row, "service_charge").add(tax);
        l.net = row.get("event_settlement_amount") != null
                ? dec(row, "event_settlement_amount")
                : l.eventAmount.subtract(l.charges);
        if (row.get("settlement_amount") != null) {
            l.cycleAmount = dec(row, "settlement_amount");
        }
        l.payment = findPayment(l);
        return l;
    }

    private MaintenancePayment findPayment(Line l) {
        try {
            if (!l.cfPaymentId.isEmpty()) {
                Optional<MaintenancePayment> p = paymentRepository.findByCashfreePaymentId(l.cfPaymentId);
                if (p.isPresent()) return p.get();
            }
            if (!l.orderId.isEmpty()) {
                return paymentRepository.findByCashfreeOrderId(l.orderId).orElse(null);
            }
        } catch (Exception ignored) {
            // duplicate / lookup problem: leave unmatched, shown as "not found"
        }
        return null;
    }

    // ==================== Sheets ====================

    private void buildSettlements(XSSFWorkbook wb, Styles s, SocietySettings settings, LocalDate start,
                                  LocalDate end, Map<String, List<Line>> cycles) {
        Sheet sheet = wb.createSheet("Settlements");
        String[] h = {"Sr", "Settlement Date", "Settlement UTR", "Payments", "Gross Amount", "Gateway Charges",
                "Settlement Amount", "Units Involved"};
        int row = titleBlock(sheet, s, settings, "Cashfree Settlements " + start + " to " + end, h.length);
        row = header(sheet, s, row, h);

        int sr = 1;
        BigDecimal tGross = BigDecimal.ZERO, tCharges = BigDecimal.ZERO, tNet = BigDecimal.ZERO;
        int tCount = 0;
        for (List<Line> cycle : cycles.values()) {
            BigDecimal gross = BigDecimal.ZERO, charges = BigDecimal.ZERO, net = BigDecimal.ZERO;
            BigDecimal declared = null;
            int count = 0;
            TreeSet<String> units = new TreeSet<>();
            for (Line l : cycle) {
                gross = gross.add(l.eventAmount);
                charges = charges.add(l.charges);
                net = net.add(l.net);
                if (l.cycleAmount != null) declared = l.cycleAmount;
                if (isPayment(l)) count++;
                String u = unitNo(l.payment);
                if (!u.isEmpty()) units.add(u);
            }
            BigDecimal settled = declared != null ? declared : net;
            Line first = cycle.get(0);
            Row r = sheet.createRow(row++);
            num(r, 0, s.text, sr++);
            text(r, 1, s.text, first.settlementDate);
            text(r, 2, s.text, first.utr);
            num(r, 3, s.text, count);
            amount(r, 4, s.amount, gross);
            amount(r, 5, s.amount, charges);
            amount(r, 6, s.amount, settled);
            text(r, 7, s.text, String.join(", ", units));
            tGross = tGross.add(gross);
            tCharges = tCharges.add(charges);
            tNet = tNet.add(settled);
            tCount += count;
        }
        Row t = sheet.createRow(row);
        text(t, 0, s.totalLabel, "TOTAL");
        num(t, 3, s.totalLabel, tCount);
        amount(t, 4, s.totalAmount, tGross);
        amount(t, 5, s.totalAmount, tCharges);
        amount(t, 6, s.totalAmount, tNet);
        autosize(sheet, h.length);
        sheet.setColumnWidth(7, 12000);
    }

    private void buildDetails(XSSFWorkbook wb, Styles s, SocietySettings settings, LocalDate start,
                              LocalDate end, List<Line> lines) {
        Sheet sheet = wb.createSheet("Settlement Details");
        String[] h = {"Sr", "Settlement Date", "Settlement UTR", "Type", "Payment Date", "Unit", "Payer",
                "Receipt No", "Order Id", "Cashfree Payment Id", "Amount", "Gateway Charges", "Net Settled"};
        int row = titleBlock(sheet, s, settings, "Settlement-wise Payment Details " + start + " to " + end, h.length);
        row = header(sheet, s, row, h);

        int sr = 1;
        BigDecimal tAmt = BigDecimal.ZERO, tChg = BigDecimal.ZERO, tNet = BigDecimal.ZERO;
        for (Line l : lines) {
            MaintenancePayment p = l.payment;
            Row r = sheet.createRow(row++);
            int c = 0;
            num(r, c++, s.text, sr++);
            text(r, c++, s.text, l.settlementDate);
            text(r, c++, s.text, l.utr);
            text(r, c++, s.text, l.eventType);
            text(r, c++, s.text, p != null && p.getPaymentDate() != null ? p.getPaymentDate().toString() : "");
            text(r, c++, s.text, p != null ? unitNo(p) : "NOT FOUND IN SYSTEM");
            text(r, c++, s.text, p != null ? p.getPayerName() : "");
            text(r, c++, s.text, p != null ? p.getReceiptNumber() : "");
            text(r, c++, s.text, l.orderId);
            text(r, c++, s.text, l.cfPaymentId);
            amount(r, c++, s.amount, l.eventAmount);
            amount(r, c++, s.amount, l.charges);
            amount(r, c++, s.amount, l.net);
            tAmt = tAmt.add(l.eventAmount);
            tChg = tChg.add(l.charges);
            tNet = tNet.add(l.net);
        }
        Row t = sheet.createRow(row);
        text(t, 0, s.totalLabel, "TOTAL");
        amount(t, 10, s.totalAmount, tAmt);
        amount(t, 11, s.totalAmount, tChg);
        amount(t, 12, s.totalAmount, tNet);
        autosize(sheet, h.length);
    }

    private void buildUnitWise(XSSFWorkbook wb, Styles s, SocietySettings settings, LocalDate start,
                               LocalDate end, List<Line> lines) {
        Sheet sheet = wb.createSheet("Unit-wise");
        String[] h = {"Sr", "Unit", "Payer", "No. of Payments", "Amount", "Gateway Charges", "Net Settled"};
        int row = titleBlock(sheet, s, settings, "Unit-wise Settled Total " + start + " to " + end, h.length);
        row = header(sheet, s, row, h);

        Map<String, Object[]> g = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Line l : lines) {
            String unit = l.payment != null ? unitNo(l.payment) : "NOT FOUND IN SYSTEM";
            String payer = l.payment != null && l.payment.getPayerName() != null ? l.payment.getPayerName() : "";
            Object[] a = g.computeIfAbsent(unit + "|" + payer,
                    k -> new Object[]{unit, payer, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[2] = (Integer) a[2] + (isPayment(l) ? 1 : 0);
            a[3] = ((BigDecimal) a[3]).add(l.eventAmount);
            a[4] = ((BigDecimal) a[4]).add(l.charges);
            a[5] = ((BigDecimal) a[5]).add(l.net);
        }
        int sr = 1;
        BigDecimal t3 = BigDecimal.ZERO, t4 = BigDecimal.ZERO, t5 = BigDecimal.ZERO;
        for (Object[] a : g.values()) {
            Row r = sheet.createRow(row++);
            num(r, 0, s.text, sr++);
            text(r, 1, s.text, (String) a[0]);
            text(r, 2, s.text, (String) a[1]);
            num(r, 3, s.text, (Integer) a[2]);
            amount(r, 4, s.amount, (BigDecimal) a[3]);
            amount(r, 5, s.amount, (BigDecimal) a[4]);
            amount(r, 6, s.amount, (BigDecimal) a[5]);
            t3 = t3.add((BigDecimal) a[3]);
            t4 = t4.add((BigDecimal) a[4]);
            t5 = t5.add((BigDecimal) a[5]);
        }
        Row t = sheet.createRow(row);
        text(t, 0, s.totalLabel, "TOTAL");
        amount(t, 4, s.totalAmount, t3);
        amount(t, 5, s.totalAmount, t4);
        amount(t, 6, s.totalAmount, t5);
        autosize(sheet, h.length);
    }

    // ==================== Helpers ====================

    private static boolean isPayment(Line l) {
        return l.eventType.isEmpty() || l.eventType.toUpperCase().contains("PAYMENT");
    }

    private static String unitNo(MaintenancePayment p) {
        return p != null && p.getUnit() != null && p.getUnit().getUnitNumber() != null
                ? p.getUnit().getUnitNumber() : "";
    }

    private static String str(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private static BigDecimal dec(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) {
                try {
                    return new BigDecimal(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static String dateOnly(String iso) {
        return iso != null && iso.length() >= 10 ? iso.substring(0, 10) : (iso != null ? iso : "");
    }

    private SocietySettings safeSettings() {
        try {
            SocietySettings st = settingsService.getSettings();
            return st != null ? st : new SocietySettings();
        } catch (Exception e) {
            return new SocietySettings();
        }
    }

    private int titleBlock(Sheet sheet, Styles s, SocietySettings settings, String title, int cols) {
        int last = Math.max(cols - 1, 1);
        Cell c0 = sheet.createRow(0).createCell(0);
        c0.setCellValue(settings.getSocietyName() != null ? settings.getSocietyName() : "Society");
        c0.setCellStyle(s.societyName);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, last));
        Cell c1 = sheet.createRow(1).createCell(0);
        c1.setCellValue(title);
        c1.setCellStyle(s.docTitle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, last));
        return 3;
    }

    private int header(Sheet sheet, Styles s, int row, String... headers) {
        Row hr = sheet.createRow(row);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = hr.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(s.tableHeader);
        }
        return row + 1;
    }

    private void text(Row r, int c, CellStyle style, String value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void num(Row r, int c, CellStyle style, Integer value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(value != null ? value : 0);
        cell.setCellStyle(style);
    }

    private void amount(Row r, int c, CellStyle style, BigDecimal value) {
        Cell cell = r.createCell(c);
        cell.setCellValue(value != null ? value.doubleValue() : 0d);
        cell.setCellStyle(style);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 600, 12000));
        }
    }

    private static final class Styles {
        final CellStyle societyName, docTitle, tableHeader, text, amount, totalLabel, totalAmount;

        Styles(Workbook wb) {
            short fmt = wb.createDataFormat().getFormat("#,##0.00");
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            Font mid = wb.createFont();
            mid.setBold(true);
            mid.setFontHeightInPoints((short) 12);
            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            Font bold = wb.createFont();
            bold.setBold(true);

            societyName = wb.createCellStyle();
            societyName.setFont(big);
            societyName.setAlignment(HorizontalAlignment.CENTER);
            docTitle = wb.createCellStyle();
            docTitle.setFont(mid);
            docTitle.setAlignment(HorizontalAlignment.CENTER);

            tableHeader = wb.createCellStyle();
            tableHeader.setFont(white);
            tableHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            box(tableHeader);

            text = wb.createCellStyle();
            box(text);

            amount = wb.createCellStyle();
            box(amount);
            amount.setDataFormat(fmt);
            amount.setAlignment(HorizontalAlignment.RIGHT);

            totalLabel = wb.createCellStyle();
            box(totalLabel);
            totalLabel.setFont(bold);
            totalLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            totalAmount = wb.createCellStyle();
            totalAmount.cloneStyleFrom(amount);
            totalAmount.setFont(bold);
            totalAmount.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalAmount.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private static void box(CellStyle st) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
    }
}
