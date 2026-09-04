package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.plugin.CostLitePluginRegistry;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.service.cost.ICostRunService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量宿主状态、启动信息和计费留存查询接口。
 */
@RestController
@RequestMapping("/cost/lite")
public class CostLiteOperationsController extends CostLiteControllerSupport {
    private final DataSource dataSource;
    private final ICostRunService runService;
    private final CostLitePluginRegistry pluginRegistry;
    private final Environment environment;

    public CostLiteOperationsController(CostLiteProperties properties,
                                        @Qualifier("costLiteDataSource") DataSource dataSource,
                                        ICostRunService runService,
                                        CostLitePluginRegistry pluginRegistry,
                                        Environment environment) {
        super(properties);
        this.dataSource = dataSource;
        this.runService = runService;
        this.pluginRegistry = pluginRegistry;
        this.environment = environment;
    }

    @GetMapping("/health")
    public AjaxResult health() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("service", "UP");
        result.put("database", databaseHealth(result));
        result.put("billingLogPersistence", properties.isPersistBillingLog() ? "ENABLED" : "DISABLED");
        boolean dispatchEnabled = environment.getProperty("cost.dispatch.enabled", Boolean.class, false);
        result.put("backgroundDispatch", dispatchEnabled ? "ENABLED" : "DISABLED");
        result.put("plugins", pluginRegistry.getPlugins().size());
        return AjaxResult.success(result);
    }

    @GetMapping("/bootstrap")
    public AjaxResult bootstrap() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("serviceName", "cost-lite-server");
        result.put("apiVersion", "v1");
        result.put("operator", properties.getOperator());
        result.put("authEnabled", properties.isAuthEnabled());
        result.put("persistBillingLog", properties.isPersistBillingLog());
        result.put("pluginEnabled", properties.isPluginEnabled());
        result.put("plugins", pluginRegistry.getPlugins().stream().map(item -> item.getCode()).sorted().collect(java.util.stream.Collectors.toList()));
        result.put("managementEndpoints", java.util.Arrays.asList(
                "/cost/scene", "/cost/fee", "/cost/variable", "/cost/rule", "/cost/formula", "/cost/publish"));
        result.put("openEndpoints", java.util.Arrays.asList(
                "/cost/open/auth/token", "/cost/open/scenes", "/cost/open/fee-template", "/cost/open/fee/calculate"));
        result.put("deploymentModes", Arrays.asList("JAR独立库", "JAR初始化到业务库"));
        return AjaxResult.success(result);
    }

    @GetMapping("/billing-log/stats")
    public AjaxResult billingLogStats(CostSimulationRecord query) {
        return success(runService.selectSimulationStats(query));
    }

    @GetMapping("/billing-log/list")
    public TableDataInfo billingLogList(CostSimulationRecord query,
                                        @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(runService.selectSimulationList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/billing-log/{simulationId}")
    public AjaxResult billingLogDetail(@PathVariable Long simulationId) {
        return success(runService.selectSimulationDetail(simulationId));
    }

    private String databaseHealth(Map<String, Object> result) {
        if (dataSource == null) {
            result.put("databaseMessage", "DataSource 未装配");
            return "DOWN";
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            result.put("databaseProduct", metadata.getDatabaseProductName());
            result.put("databaseVersion", metadata.getDatabaseProductVersion());
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            result.put("databaseName", catalog != null && !catalog.trim().isEmpty() ? catalog : schema);
            return connection.isValid(2) ? "UP" : "DEGRADED";
        } catch (Exception exception) {
            result.put("databaseMessage", exception.getMessage() == null ? "数据库连接失败" : exception.getMessage());
            return "DOWN";
        }
    }
}
