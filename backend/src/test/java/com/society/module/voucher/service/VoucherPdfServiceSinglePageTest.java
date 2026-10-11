package com.society.module.voucher.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.society.enums.PaymentMode;
import com.society.enums.VoucherStatus;
import com.society.enums.VoucherType;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.VoucherDocumentRepository;
import com.society.module.voucher.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies the generated voucher PDF fits on a single A4 page,
 * including the worst case where the TDS section AND the receiver
 * signature are both present.
 */
@ExtendWith(MockitoExtension.class)
class VoucherPdfServiceSinglePageTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherDocumentRepository voucherDocumentRepository;
    @Mock
    private SocietySettingsService settingsService;

    @InjectMocks
    private VoucherPdfService voucherPdfService;

    private SocietySettings settings() {
        SocietySettings s = new SocietySettings();
        s.setSocietyName("Green Valley Co-operative Housing Society Limited");
        s.setRegistrationNumber("REG/2001/XYZ-12345");
        s.setRegistrationDate("01-04-2001");
        s.setAddressLine1("Plot No. 42, Sector 15, Near Central Park");
        s.setAddressLine2("Opposite Metro Station, Main Road");
        s.setCity("Pune");
        s.setState("Maharashtra");
        s.setPincode("411001");
        s.setPhone("020-12345678");
        s.setEmail("office@greenvalley.example.com");
        s.setChairmanName("Rajesh Kumar Sharma");
        s.setSecretaryName("Anita Desai");
        s.setTreasurerName("Vikram Patel");
        return s;
    }

    private Voucher tdsVoucher() {
        Voucher voucher = Voucher.builder()
                .voucherId(1L)
                .voucherNumber("PPV/CD/2026-27/011")
                .voucherDate(LocalDate.of(2026, 8, 15))
                .voucherType(VoucherType.PAYMENT)
                .category("SECURITY_SERVICES")
                .description("Monthly security services charges for the housing society premises including guards and CCTV monitoring.")
                .amount(new BigDecimal("40000.00"))
                .paymentMode(PaymentMode.CHEQUE)
                .referenceNumber("CHQ-000123456")
                .billInvoiceNumber("INV-2026-0789")
                .billDate(LocalDate.of(2026, 8, 10))
                .status(VoucherStatus.FINAL)
                .financialYear("2026-27")
                .tdsApplicable(true)
                .tdsSection("194C")
                .tdsRate(new BigDecimal("2.00"))
                .tdsAmount(new BigDecimal("800.00"))
                .netPayable(new BigDecimal("39200.00"))
                .build();
        voucher.setCreatedBy("treasurer@greenvalley");
        return voucher;
    }

    private int pageCount(byte[] pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void tdsVoucherFitsOnSinglePage() throws Exception {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(tdsVoucher()));
        lenient().when(settingsService.getSettings()).thenReturn(settings());

        byte[] pdf = voucherPdfService.generateVoucherPdf(1L, false);

        assertThat(pageCount(pdf))
                .as("Voucher with TDS section + receiver signature must stay on one page")
                .isEqualTo(1);
    }

    @Test
    void nonTdsVoucherFitsOnSinglePage() throws Exception {
        Voucher v = tdsVoucher();
        v.setTdsApplicable(false);
        v.setTdsAmount(null);
        v.setNetPayable(null);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(v));
        lenient().when(settingsService.getSettings()).thenReturn(settings());

        byte[] pdf = voucherPdfService.generateVoucherPdf(1L, false);

        assertThat(pageCount(pdf)).isEqualTo(1);
    }
}
