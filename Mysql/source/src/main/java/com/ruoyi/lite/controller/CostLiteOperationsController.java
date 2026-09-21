package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.plugin.CostLitePluginRegistry;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostSimulationRecord;
import com.ruoyi.system.service.cost.ICostRunService;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 轻量宿主状态、启动信息和计费留存查询接口。
 */
@RestController
@RequestMapping("/cost/lite")
public class CostLiteOperationsController extends CostLiteControllerSupport {
    private static final List<String> REQUIRED_CORE_TABLES = Arrays.asList(
            "cost_scene", "cost_fee_item", "cost_variable_group", "cost_variable",
            "cost_fee_variable_rel", "cost_rule", "cost_rule_condition", "cost_rule_tier",
            "cost_formula", "cost_formula_version", "cost_publish_version", "cost_publish_snapshot",
            "cost_simulation_record", "cost_audit_log", "sys_dict_type", "sys_dict_data");
    private static final List<String> REQUIRED_FORMAL_TABLES = Arrays.asList(
            "cost_bill_period", "cost_recalc_order", "cost_alarm_record", "cost_access_profile",
            "cost_calc_input_batch", "cost_calc_input_batch_item", "cost_calc_task",
            "cost_calc_task_detail", "cost_calc_task_partition", "cost_result_ledger",
            "cost_result_trace", "cost_open_app");

    private final DataSource dataSource;
    private final ICostRunService runService;
    private final CostLitePluginRegistry pluginRegistry;
    private final Environment environment;

    public CostLiteOperationsController(CostLiteProperties properties,
                                        ConfigurableListableBeanFactory beanFactory,
                                        ICostRunService runService,
                                        CostLitePluginRegistry pluginRegistry,
                                        Environment environment) {
        super(properties);
        this.dataSource = beanFactory.getBean("costLiteDataSource", DataSource.class);
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
        result.put("serviceName", "cost-lite-core");
        result.put("apiVersion", "v1");
        result.put("operator", properties.getOperator());
        result.put("authEnabled", properties.isAuthEnabled());
        result.put("persistBillingLog", properties.isPersistBillingLog());
        result.put("pluginEnabled", properties.isPluginEnabled());
        result.put("formalEnabled", properties.isFormalEnabled());
        result.put("openApiEnabled", properties.isOpenApiEnabled());
        result.put("plugins", pluginRegistry.getPlugins().stream().map(item -> item.getCode()).sorted().collect(java.util.stream.Collectors.toList()));
        result.put("managementEndpoints", java.util.Arrays.asList(
                "/cost/scene", "/cost/fee", "/cost/variable", "/cost/rule", "/cost/formula", "/cost/publish"));
        result.put("openEndpoints", properties.isOpenApiEnabled()
                ? java.util.Arrays.asList("/cost/open/auth/token", "/cost/open/scenes", "/cost/open/fee-template", "/cost/open/fee/calculate")
                : java.util.Collections.emptyList());
        result.put("formalEndpoints", properties.isFormalEnabled()
                ? java.util.Arrays.asList("/cost/run/task/**", "/cost/run/result/**", "/cost/run/trace/**")
                : java.util.Collections.emptyList());
        result.put("deploymentModes", Arrays.asList("同进程 Starter 复用宿主数据源",
                "同进程 Starter 使用专用计费库"));
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
        String configuredMode = environment.getProperty("cost.lite.datasource.mode", "");
        result.put("dataSourceMode", configuredMode == null || configuredMode.trim().isEmpty()
                ? "legacy-url-detection" : configuredMode.trim().toLowerCase(Locale.ROOT));
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            result.put("databaseProduct", metadata.getDatabaseProductName());
            result.put("databaseVersion", metadata.getDatabaseProductVersion());
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            result.put("databaseName", catalog != null && !catalog.trim().isEmpty() ? catalog : schema);
            if (!connection.isValid(2)) {
                return "DEGRADED";
            }

            List<String> requiredTables = new ArrayList<>(REQUIRED_CORE_TABLES);
            if (properties.isFormalEnabled()) {
                requiredTables.addAll(REQUIRED_FORMAL_TABLES);
            }
            Set<String> existingTables = findTableNames(metadata, catalog, schema);
            List<String> missingTables = new ArrayList<>();
            for (String requiredTable : requiredTables) {
                if (!existingTables.contains(requiredTable.toUpperCase(Locale.ROOT))) {
                    missingTables.add(requiredTable);
                }
            }
            if (!missingTables.isEmpty()) {
                result.put("missingTables", missingTables);
                result.put("databaseMessage", "当前数据源缺少轻量计费表，请按 dataSourceMode 在正确数据库执行对应 SQL");
                return "DOWN";
            }
            return "UP";
        } catch (Exception exception) {
            result.put("databaseMessage", exception.getMessage() == null ? "数据库连接失败" : exception.getMessage());
            return "DOWN";
        }
    }

    private Set<String> findTableNames(DatabaseMetaData metadata, String catalog, String schema) throws SQLException {
        Set<String> tableNames = new HashSet<>();
        collectTableNames(metadata, catalog, schema, tableNames);
        if (tableNames.isEmpty() && schema != null && !schema.trim().isEmpty()) {
            collectTableNames(metadata, catalog, null, tableNames);
        }
        if (tableNames.isEmpty() && catalog != null && !catalog.trim().isEmpty()) {
            collectTableNames(metadata, null, schema, tableNames);
        }
        return tableNames;
    }

    private void collectTableNames(DatabaseMetaData metadata, String catalog, String schema,
                                   Set<String> tableNames) throws SQLException {
        try (ResultSet tables = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName != null && !tableName.trim().isEmpty()) {
                    tableNames.add(tableName.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }
}
