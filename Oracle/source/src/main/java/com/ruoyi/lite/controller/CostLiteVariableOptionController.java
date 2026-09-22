package com.ruoyi.lite.controller;

import com.costplatform.lite.extension.CostLiteOption;
import com.costplatform.lite.extension.CostLiteOptionPage;
import com.costplatform.lite.extension.CostLiteOptionProvider;
import com.costplatform.lite.extension.CostLiteOptionQuery;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.system.domain.cost.CostVariable;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.service.cost.ICostVariableService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Rule-editor option endpoint for typed matrix and advanced conditions. */
@RestController
@RequestMapping("/cost/variable/options")
public class CostLiteVariableOptionController extends CostLiteControllerSupport {
    private static final String OPTION_NONE = "NONE";
    private static final String OPTION_PLATFORM_DICT = "PLATFORM_DICT";
    private static final String OPTION_BUSINESS_DICT = "BUSINESS_DICT";
    private static final String OPTION_BUSINESS_MASTER = "BUSINESS_MASTER";

    private final ICostVariableService variableService;
    private final SysDictDataMapper dictDataMapper;
    private final List<CostLiteOptionProvider> optionProviders;

    public CostLiteVariableOptionController(CostLiteProperties properties,
                                            ICostVariableService variableService,
                                            SysDictDataMapper dictDataMapper,
                                            List<CostLiteOptionProvider> optionProviders) {
        super(properties);
        this.variableService = variableService;
        this.dictDataMapper = dictDataMapper;
        this.optionProviders = optionProviders == null
                ? Collections.<CostLiteOptionProvider>emptyList() : optionProviders;
    }

    @GetMapping
    public AjaxResult options(@RequestParam("variableId") Long variableId,
                              @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
                              @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
                              @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {
        CostVariable variable = variableService.selectVariableById(variableId);
        if (variable == null) {
            return error("要素不存在，请刷新后重试");
        }
        String optionSourceType = resolveOptionSourceType(variable);
        int normalizedPageNum = Math.max(1, pageNum == null ? 1 : pageNum);
        int normalizedPageSize = Math.min(200, Math.max(1, pageSize == null ? 50 : pageSize));
        if (OPTION_NONE.equals(optionSourceType)) {
            return success(new CostLiteOptionPage());
        }
        if (OPTION_PLATFORM_DICT.equals(optionSourceType)) {
            return success(queryPlatformDictionary(variable, keyword, normalizedPageNum, normalizedPageSize));
        }
        if (!OPTION_BUSINESS_DICT.equals(optionSourceType) && !OPTION_BUSINESS_MASTER.equals(optionSourceType)) {
            return error("不支持的要素选项来源类型：" + optionSourceType);
        }

        CostLiteOptionQuery query = new CostLiteOptionQuery();
        query.setVariableId(variable.getVariableId());
        query.setVariableCode(variable.getVariableCode());
        query.setOptionSourceType(optionSourceType);
        query.setOptionSourceCode(StringUtils.defaultIfEmpty(variable.getOptionSourceCode(), ""));
        query.setOptionConfigJson(variable.getOptionConfigJson());
        query.setKeyword(StringUtils.trim(keyword));
        query.setPageNum(normalizedPageNum);
        query.setPageSize(normalizedPageSize);
        for (CostLiteOptionProvider provider : optionProviders) {
            if (provider != null && provider.supports(optionSourceType, query.getOptionSourceCode())) {
                CostLiteOptionPage result = provider.query(query);
                if (result == null) {
                    throw new ServiceException("业务选项适配器未返回结果：" + query.getOptionSourceCode());
                }
                return success(result);
            }
        }
        return error("未找到业务选项适配器：" + optionSourceType + "/" + query.getOptionSourceCode()
                + "。请在宿主业务系统提供 CostLiteOptionProvider Bean");
    }

    private String resolveOptionSourceType(CostVariable variable) {
        String sourceType = StringUtils.trim(variable.getOptionSourceType());
        if (StringUtils.isEmpty(sourceType)) {
            return isLegacyPlatformDictionary(variable) ? OPTION_PLATFORM_DICT : OPTION_NONE;
        }
        if (OPTION_NONE.equalsIgnoreCase(sourceType) && isLegacyPlatformDictionary(variable)) {
            return OPTION_PLATFORM_DICT;
        }
        return sourceType.toUpperCase(Locale.ROOT);
    }

    private boolean isLegacyPlatformDictionary(CostVariable variable) {
        return StringUtils.isNotEmpty(variable.getDictType())
                && ("DICT".equalsIgnoreCase(StringUtils.defaultString(variable.getSourceType()))
                || "DICT".equalsIgnoreCase(StringUtils.defaultString(variable.getVariableType())));
    }

    private CostLiteOptionPage queryPlatformDictionary(CostVariable variable, String keyword,
                                                       int pageNum, int pageSize) {
        String dictType = StringUtils.trim(variable.getDictType());
        if (StringUtils.isEmpty(dictType) || !dictType.startsWith("cost_")) {
            throw new ServiceException("平台字典选项必须使用 cost_ 前缀字典");
        }
        String normalizedKeyword = StringUtils.defaultIfEmpty(StringUtils.trim(keyword), "").toLowerCase(Locale.ROOT);
        // 历史规则可能仍引用已停用编码；返回全量并由前端置灰，新增保存仍由服务端校验启用状态。
        String storageDictType = properties.getDictionary().resolveType(dictType);
        List<SysDictData> sourceRows = dictDataMapper.selectAllDictDataByType(storageDictType);
        List<CostLiteOption> rows = new ArrayList<>();
        if (sourceRows != null) {
            List<SysDictData> sortedRows = new ArrayList<>(sourceRows);
            Collections.sort(sortedRows, new Comparator<SysDictData>() {
                @Override
                public int compare(SysDictData left, SysDictData right) {
                    if (left == null) return right == null ? 0 : 1;
                    if (right == null) return -1;
                    int sortCompare = Long.compare(
                            left.getDictSort() == null ? Long.MAX_VALUE : left.getDictSort(),
                            right.getDictSort() == null ? Long.MAX_VALUE : right.getDictSort());
                    if (sortCompare != 0) return sortCompare;
                    return StringUtils.defaultIfEmpty(left.getDictValue(), "")
                            .compareTo(StringUtils.defaultIfEmpty(right.getDictValue(), ""));
                }
            });
            for (SysDictData row : sortedRows) {
                if (row == null) {
                    continue;
                }
                if (!normalizedKeyword.isEmpty()
                        && !containsIgnoreCase(row.getDictLabel(), normalizedKeyword)
                        && !containsIgnoreCase(row.getDictValue(), normalizedKeyword)
                        && !containsIgnoreCase(properties.getDictionary()
                        .resolveCanonicalValue(dictType, row.getDictValue()), normalizedKeyword)) {
                    continue;
                }
                CostLiteOption option = new CostLiteOption(
                        properties.getDictionary().resolveCanonicalValue(dictType, row.getDictValue()),
                        row.getDictLabel());
                option.setDisabled(StringUtils.isNotEmpty(row.getStatus()) && !"0".equals(row.getStatus()));
                rows.add(option);
            }
        }
        long total = rows.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        return new CostLiteOptionPage(new ArrayList<>(rows.subList(fromIndex, toIndex)), total, toIndex < total);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.defaultIfEmpty(value, "").toLowerCase(Locale.ROOT).contains(keyword);
    }
}
