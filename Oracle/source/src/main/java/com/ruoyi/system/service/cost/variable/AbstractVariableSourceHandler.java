package com.ruoyi.system.service.cost.variable;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.cost.CostVariable;
import org.springframework.http.MediaType;

import java.util.Locale;

abstract class AbstractVariableSourceHandler implements VariableSourceHandler {
    protected void clearSourceSystem(CostVariable variable) {
        variable.setSourceSystem("");
    }

    protected void clearDict(CostVariable variable) {
        variable.setDictType("");
    }

    protected void clearRemote(CostVariable variable) {
        variable.setRemoteApi("");
        variable.setRequestMethod("GET");
        variable.setContentType(MediaType.APPLICATION_JSON_VALUE);
        variable.setQueryConfigJson(null);
        variable.setRequestHeadersJson(null);
        variable.setBodyTemplateJson(null);
        variable.setAuthType("NONE");
        variable.setAuthConfigJson(null);
        variable.setResponseConfigJson(null);
        variable.setMappingConfigJson(null);
        variable.setPageConfigJson(null);
        variable.setAdapterType("STANDARD");
        variable.setAdapterConfigJson(null);
        variable.setSyncMode("REALTIME");
        variable.setCachePolicy("MANUAL_REFRESH");
        variable.setFallbackPolicy("FAIL_FAST");
    }

    protected void clearDataPath(CostVariable variable) {
        variable.setDataPath("");
    }

    protected void clearFormula(CostVariable variable) {
        variable.setFormulaExpr(null);
        variable.setFormulaCode("");
    }

    protected void normalizeDataPath(CostVariable variable) {
        variable.setDataPath(StringUtils.trim(variable.getDataPath()));
    }

    protected void normalizeRemote(CostVariable variable) {
        variable.setRemoteApi(StringUtils.trim(variable.getRemoteApi()));
        variable.setRequestMethod(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getRequestMethod()), "GET").toUpperCase(Locale.ROOT));
        variable.setContentType(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getContentType()), MediaType.APPLICATION_JSON_VALUE));
        normalizeDataPath(variable);
        variable.setAuthType(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getAuthType()), "NONE"));
        variable.setSyncMode(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getSyncMode()), "REALTIME"));
        variable.setCachePolicy(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getCachePolicy()), "MANUAL_REFRESH"));
        variable.setFallbackPolicy(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getFallbackPolicy()), "FAIL_FAST"));
        variable.setAdapterType(StringUtils.defaultIfEmpty(StringUtils.trim(variable.getAdapterType()), "STANDARD"));
    }
}
