package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.system.mapper.SysDictDataMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 轻量工作台字典接口。
 *
 * <p>只读取目标轻量库中的计费字典数据，不访问宿主系统的其他字典或系统表。
 * 类型白名单规则同时避免把任意系统字典暴露成公共接口。</p>
 */
@RestController
@RequestMapping("/cost/dictionary")
public class CostLiteDictionaryController extends CostLiteControllerSupport {
    private static final Pattern COST_DICT_TYPE = Pattern.compile("cost_[a-z0-9_]+");

    private final SysDictDataMapper dictDataMapper;

    public CostLiteDictionaryController(CostLiteProperties properties,
                                        SysDictDataMapper dictDataMapper) {
        super(properties);
        this.dictDataMapper = dictDataMapper;
    }

    @GetMapping("/options")
    public AjaxResult options(@RequestParam(value = "types", required = false) String types) {
        Set<String> requestedTypes = parseTypes(types);
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        for (String dictType : requestedTypes) {
            List<Map<String, String>> options = new ArrayList<>();
            List<SysDictData> rows = dictDataMapper.selectDictDataByType(dictType);
            if (rows != null) {
                for (SysDictData row : rows) {
                    Map<String, String> option = new LinkedHashMap<>();
                    option.put("label", row.getDictLabel());
                    option.put("value", row.getDictValue());
                    options.add(option);
                }
            }
            result.put(dictType, options);
        }
        return success(result);
    }

    private Set<String> parseTypes(String types) {
        Set<String> result = new LinkedHashSet<>();
        if (types == null || types.trim().isEmpty()) {
            return result;
        }
        for (String candidate : types.split(",")) {
            String dictType = candidate == null ? "" : candidate.trim();
            if (COST_DICT_TYPE.matcher(dictType).matches()) {
                result.add(dictType);
            }
        }
        return result;
    }
}
