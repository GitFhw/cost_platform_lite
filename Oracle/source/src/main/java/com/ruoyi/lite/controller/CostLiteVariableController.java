package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostVariable;
import com.ruoyi.system.domain.vo.CostVariableCopyRequest;
import com.ruoyi.system.service.cost.ICostVariableService;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 轻量要素维护接口。页面称“要素”，数据仍复用母体变量实体。
 */
@RestController
@RequestMapping("/cost/variable")
public class CostLiteVariableController extends CostLiteControllerSupport {
    private final ICostVariableService variableService;

    public CostLiteVariableController(ICostVariableService variableService, CostLiteProperties properties) {
        super(properties);
        this.variableService = variableService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostVariable query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(variableService.selectVariableList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostVariable query) {
        return success(variableService.selectVariableStats(query));
    }

    @GetMapping("/governance/{variableId}")
    public AjaxResult governance(@PathVariable Long variableId) {
        return success(variableService.selectVariableGovernanceCheck(variableId));
    }

    @GetMapping("/optionselect")
    public AjaxResult options(CostVariable query) {
        return success(variableService.selectVariableOptions(query));
    }

    @GetMapping("/{variableId}")
    public AjaxResult detail(@PathVariable Long variableId) {
        return success(variableService.selectVariableById(variableId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostVariable variable) {
        variable.setCreateBy(operator());
        variable.setUpdateBy(operator());
        return variableService.checkVariableCodeUnique(variable)
                ? toAjax(variableService.insertVariable(variable))
                : error("同一场景下要素编码已存在");
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostVariable variable) {
        variable.setUpdateBy(operator());
        return variableService.checkVariableCodeUnique(variable)
                ? toAjax(variableService.updateVariable(variable))
                : error("同一场景下要素编码已存在");
    }

    @PostMapping("/copy")
    public AjaxResult copy(@RequestBody CostVariableCopyRequest request) {
        return success(variableService.copyVariable(request));
    }

    /**
     * 测试当前远程要素配置对应的第三方接口。
     */
    @PostMapping("/remote/test")
    public AjaxResult testRemote(@RequestBody Map<String, Object> request) {
        return success(variableService.testRemoteConnection(request));
    }

    /**
     * 预览当前远程要素的原始数据和映射结果。
     */
    @PostMapping("/remote/preview")
    public AjaxResult previewRemote(@RequestBody Map<String, Object> request) {
        return success(variableService.previewRemoteData(request));
    }

    /**
     * 复用母体的远程要素缓存刷新检查入口。
     */
    @PostMapping("/remote/refresh")
    public AjaxResult refreshRemote(@RequestBody(required = false) Map<String, Object> request) {
        Long sceneId = null;
        if (request != null && request.get("sceneId") != null) {
            try {
                sceneId = Long.valueOf(String.valueOf(request.get("sceneId")));
            } catch (NumberFormatException exception) {
                return error("场景编号必须是数字");
            }
        }
        return success(variableService.refreshRemoteCache(sceneId));
    }

    @DeleteMapping("/{variableIds}")
    public AjaxResult remove(@PathVariable Long[] variableIds) {
        return toAjax(variableService.deleteVariableByIds(variableIds));
    }
}
