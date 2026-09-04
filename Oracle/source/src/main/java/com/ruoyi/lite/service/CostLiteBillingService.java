package com.ruoyi.lite.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.domain.cost.bo.CostFeeCalculateBo;
import com.ruoyi.system.service.cost.ICostRunService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量同步计费门面。
 */
@Service
public class CostLiteBillingService {
    private final ICostRunService runService;
    private final CostLiteBillingLogWriter logWriter;
    private final CostLiteProperties properties;

    public CostLiteBillingService(ICostRunService runService,
                                  CostLiteBillingLogWriter logWriter,
                                  CostLiteProperties properties) {
        this.runService = runService;
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public Map<String, Object> calculate(CostFeeCalculateBo request) {
        try {
            Map<String, Object> result = runService.calculateFee(request);
            CostSimulationRecord log = logWriter.writeSuccess(request, result);
            if (log != null) {
                result = new LinkedHashMap<>(result);
                result.put("billingLogId", log.getSimulationId());
                result.put("billingLogNo", log.getSimulationNo());
                result.put("billingLogStatus", log.getStatus());
            }
            return result;
        } catch (RuntimeException exception) {
            CostSimulationRecord log = logWriter.writeFailure(request, exception);
            Long logId = log == null ? null : log.getSimulationId();
            String message = StringUtils.defaultIfEmpty(exception.getMessage(), "计费执行失败");
            Integer code = null;
            if (exception instanceof ServiceException) {
                code = ((ServiceException) exception).getCode();
            }
            throw new CostLiteBillingException(message, code, logId, exception);
        }
    }

    public boolean isPersistenceEnabled() {
        return properties.isPersistBillingLog();
    }
}
