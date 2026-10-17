package com.society.module.visitor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the gate picker's wing/floor derivation
 * ({@link VisitorRequestService#deriveWingFloor}). Covers the society's unit-number
 * formats (with and without a dash) and the "stored columns win" rule.
 */
class VisitorUnitParsingTest {

    @Test
    void parsesDashFormatD105() {
        String[] wf = VisitorRequestService.deriveWingFloor("D-105", null, null);
        assertThat(wf[0]).isEqualTo("D");
        assertThat(wf[1]).isEqualTo("1");   // floor 1, flat 05
    }

    @Test
    void parsesNoDashFormatD1105() {
        String[] wf = VisitorRequestService.deriveWingFloor("D1105", null, null);
        assertThat(wf[0]).isEqualTo("D");
        assertThat(wf[1]).isEqualTo("11");  // floor 11, flat 05
    }

    @Test
    void parsesNoDashFormatC101() {
        String[] wf = VisitorRequestService.deriveWingFloor("C101", null, null);
        assertThat(wf[0]).isEqualTo("C");
        assertThat(wf[1]).isEqualTo("1");   // floor 1, flat 01
    }

    @Test
    void parsesFloor14D1405() {
        String[] wf = VisitorRequestService.deriveWingFloor("D-1405", null, null);
        assertThat(wf[0]).isEqualTo("D");
        assertThat(wf[1]).isEqualTo("14");  // floor 14, flat 05
    }

    @Test
    void storedColumnsTakePrecedenceOverParsing() {
        String[] wf = VisitorRequestService.deriveWingFloor("D-1405", "D", "2");
        assertThat(wf[0]).isEqualTo("D");
        assertThat(wf[1]).isEqualTo("2");   // trusts the floor column, not the parse
    }

    @Test
    void shortDigitBlockHasZeroFloor() {
        String[] wf = VisitorRequestService.deriveWingFloor("C-05", null, null);
        assertThat(wf[0]).isEqualTo("C");
        assertThat(wf[1]).isEqualTo("0");
    }

    @Test
    void unparseableNumberYieldsNulls() {
        String[] wf = VisitorRequestService.deriveWingFloor("SHOP", null, null);
        assertThat(wf[0]).isNull();
        assertThat(wf[1]).isNull();
    }
}
