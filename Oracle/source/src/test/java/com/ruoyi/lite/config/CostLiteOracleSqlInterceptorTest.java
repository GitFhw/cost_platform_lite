package com.ruoyi.lite.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostLiteOracleSqlInterceptorTest {
    @Test
    void convertsMySqlDateFormatToOracleToChar() {
        assertEquals("TO_CHAR(create_time,'YYYYMMDD')",
                CostLiteOracleSqlInterceptor.rewriteForOracle("date_format(create_time,'%Y%m%d')"));
    }

    @Test
    void keepsDistinctGroupConcatSemantics() {
        String sql = CostLiteOracleSqlInterceptor.rewriteForOracle(
                "group_concat(distinct c.display_name order by c.sort_no asc separator '; ')");
        assertTrue(sql.contains("LISTAGG(DISTINCT c.display_name,'; ')") ||
                sql.contains("LISTAGG(DISTINCT c.display_name, '; ')") ||
                sql.contains("LISTAGG(DISTINCT c.display_name,';')"));
        assertTrue(sql.contains("WITHIN GROUP (ORDER BY c.sort_no asc)"));
    }
}
