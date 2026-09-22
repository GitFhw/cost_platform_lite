package com.ruoyi.system.service.cost.execution;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostConditionValueSupportTest {
    @Test
    void numericComparisonUsesValueSemanticsNotStringFormatting() {
        assertTrue(CostConditionValueSupport.matches(new BigDecimal("1.00"), "1", "EQ", "NUMBER",
                "NUMBER", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches(new BigDecimal("10.50"), "10", "GT", "NUMBER",
                "NUMBER", "INPUT", "NONE", null));
        assertFalse(CostConditionValueSupport.matches("not-a-number", "10", "GT", "NUMBER",
                "NUMBER", "INPUT", "NONE", null));
    }

    @Test
    void dateBetweenIsChronologicalAndNotBigDecimalParsing() {
        assertTrue(CostConditionValueSupport.matches("2026-09-21", "2026-09-01,2026-09-30", "BETWEEN", "DATE",
                "DATE", "INPUT", "NONE", null));
        assertFalse(CostConditionValueSupport.matches("2026-10-01", "2026-09-01,2026-09-30", "BETWEEN", "DATE",
                "DATE", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.isValidCompareValue("2026-09-01,2026-09-30", "BETWEEN", "DATE",
                "DATE", "INPUT", "NONE", null));
        assertFalse(CostConditionValueSupport.isValidCompareValue("2026/09/01,2026/09/30", "BETWEEN", "DATE",
                "DATE", "INPUT", "NONE", null));
        assertFalse(CostConditionValueSupport.isValidCompareValue("2026-09-30,2026-09-01", "BETWEEN", "DATE",
                "DATE", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches("2026-09-21 08:30:00", "2026-09-21 08:00:00", "GT", "TIMESTAMP",
                "TIMESTAMP", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches("2026-09", "2026-01", "GT", "MONTH",
                "MONTH", "INPUT", "NONE", null));
    }

    @Test
    void legacyVariableTypeFallsBackWhenDataTypeIsMissing() {
        assertTrue(CostConditionValueSupport.matches("2026-09-21", "2026-09-01", "GT", null,
                "DATE", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.isValidCompareValue("2026-09-01,2026-09-30", "BETWEEN", null,
                "DATE", "INPUT", "NONE", null));
    }

    @Test
    void timeOfDayBetweenSupportsCrossMidnightWindows() {
        assertTrue(CostConditionValueSupport.matches("23:30:00", "22:00:00,02:00:00", "BETWEEN", "TIME_OF_DAY",
                "TIME", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches("01:30:00", "22:00:00,02:00:00", "BETWEEN", "TIME_OF_DAY",
                "TIME", "INPUT", "NONE", null));
        assertFalse(CostConditionValueSupport.matches("12:00:00", "22:00:00,02:00:00", "BETWEEN", "TIME_OF_DAY",
                "TIME", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches("22:00:00", "22:00:00,22:00:00", "BETWEEN", "TIME_OF_DAY",
                "TIME", "INPUT", "NONE", null));
    }

    @Test
    void optionInUsesStableCodeMembers() {
        assertTrue(CostConditionValueSupport.matches("OUT", "IN,OUT", "IN", "STRING",
                "DICT", "DICT", "PLATFORM_DICT", "cost_trade_type"));
        assertFalse(CostConditionValueSupport.matches("CANCEL", "IN,OUT", "IN", "STRING",
                "DICT", "DICT", "PLATFORM_DICT", "cost_trade_type"));
    }

    @Test
    void nullOperatorsAndTypedOperatorPolicyAreExplicit() {
        assertTrue(CostConditionValueSupport.matches("", null, "IS_NULL", "STRING",
                "TEXT", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.matches("OUT", null, "IS_NOT_NULL", "STRING",
                "TEXT", "INPUT", "NONE", null));
        assertTrue(CostConditionValueSupport.isOperatorAllowed("IN", CostConditionValueSupport.ValueKind.OPTION));
        assertFalse(CostConditionValueSupport.isOperatorAllowed("GT", CostConditionValueSupport.ValueKind.TEXT));
        assertFalse(CostConditionValueSupport.isOperatorAllowed("BETWEEN", CostConditionValueSupport.ValueKind.BOOLEAN));
    }
}
