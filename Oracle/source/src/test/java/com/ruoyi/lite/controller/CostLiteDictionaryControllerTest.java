package com.ruoyi.lite.controller;

import com.costplatform.lite.extension.CostLiteOption;
import com.costplatform.lite.extension.CostLiteOptionPage;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.domain.cost.CostVariable;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.service.cost.ICostVariableService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CostLiteDictionaryControllerTest {
    @Test
    void dictionaryEndpointReturnsDisabledItemsForHistoricalEcho() {
        SysDictDataMapper mapper = mock(SysDictDataMapper.class);
        when(mapper.selectAllDictDataByType("cost_trade_type")).thenReturn(List.of(
                dictRow(10L, 10L, "外贸", "OUT", "0"),
                dictRow(11L, 20L, "历史内贸", "IN", "1")));

        CostLiteDictionaryController controller = new CostLiteDictionaryController(
                new CostLiteProperties(), mapper);
        AjaxResult result = controller.options("cost_trade_type");

        assertTrue(result.isSuccess());
        Map<?, ?> dictionary = (Map<?, ?>) result.get(AjaxResult.DATA_TAG);
        List<?> options = (List<?>) dictionary.get("cost_trade_type");
        assertEquals(2, options.size());
        assertFalse(((Map<?, ?>) options.get(0)).containsKey("disabled"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) options.get(1)).get("disabled"));
        verify(mapper).selectAllDictDataByType("cost_trade_type");
        verify(mapper, never()).selectDictDataByType("cost_trade_type");
    }

    @Test
    void platformVariableOptionsKeepDisabledItemsVisibleButNotSelectable() {
        SysDictDataMapper mapper = mock(SysDictDataMapper.class);
        when(mapper.selectAllDictDataByType("cost_trade_type")).thenReturn(List.of(
                dictRow(10L, 10L, "外贸", "OUT", "0"),
                dictRow(11L, 20L, "历史内贸", "IN", "1")));
        ICostVariableService variableService = mock(ICostVariableService.class);
        CostVariable variable = new CostVariable();
        variable.setVariableId(7L);
        variable.setVariableCode("tradeType");
        variable.setVariableType("DICT");
        variable.setSourceType("DICT");
        variable.setDictType("cost_trade_type");
        when(variableService.selectVariableById(7L)).thenReturn(variable);

        CostLiteVariableOptionController controller = new CostLiteVariableOptionController(
                new CostLiteProperties(), variableService, mapper, List.of());
        AjaxResult result = controller.options(7L, "", 1, 50);

        assertTrue(result.isSuccess());
        CostLiteOptionPage page = (CostLiteOptionPage) result.get(AjaxResult.DATA_TAG);
        assertEquals(2, page.getTotal());
        List<CostLiteOption> options = page.getRows();
        assertFalse(options.get(0).isDisabled());
        assertTrue(options.get(1).isDisabled());
        verify(mapper).selectAllDictDataByType("cost_trade_type");
        verify(mapper, never()).selectDictDataByType("cost_trade_type");
    }

    @Test
    void dictionaryMappingQueriesHostTypeAndReturnsStableCostCode() {
        SysDictDataMapper mapper = mock(SysDictDataMapper.class);
        when(mapper.selectAllDictDataByType("host_trade_type")).thenReturn(List.of(
                dictRow(10L, 10L, "外贸", "Y", "0")));
        CostLiteProperties properties = new CostLiteProperties();
        properties.getDictionary().setTypeMappings(Map.of("cost_trade_type", "host_trade_type"));
        properties.getDictionary().setValueMappings(Map.of(
                "cost_trade_type", Map.of("OUT", "Y")));

        AjaxResult result = new CostLiteDictionaryController(properties, mapper)
                .options("cost_trade_type");

        Map<?, ?> dictionary = (Map<?, ?>) result.get(AjaxResult.DATA_TAG);
        List<?> options = (List<?>) dictionary.get("cost_trade_type");
        assertEquals("OUT", ((Map<?, ?>) options.get(0)).get("value"));
        verify(mapper).selectAllDictDataByType("host_trade_type");
    }

    private static SysDictData dictRow(Long code, Long sort, String label, String value, String status) {
        SysDictData row = new SysDictData();
        row.setDictCode(code);
        row.setDictSort(sort);
        row.setDictLabel(label);
        row.setDictValue(value);
        row.setDictType("cost_trade_type");
        row.setStatus(status);
        return row;
    }
}
