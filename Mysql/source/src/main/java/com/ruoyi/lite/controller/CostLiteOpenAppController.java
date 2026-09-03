package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostOpenApp;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.service.cost.ICostOpenAppService;
import com.ruoyi.system.service.cost.ICostSceneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放应用管理接口。
 */
@RestController
@RequestMapping("/cost/openApp")
public class CostLiteOpenAppController extends CostLiteControllerSupport {
    private final ICostOpenAppService openAppService;
    private final ICostSceneService sceneService;

    public CostLiteOpenAppController(ICostOpenAppService openAppService,
                                     ICostSceneService sceneService,
                                     CostLiteProperties properties) {
        super(properties);
        this.openAppService = openAppService;
        this.sceneService = sceneService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostOpenApp query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(openAppService.selectOpenAppList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/sceneOptions")
    public AjaxResult sceneOptions() {
        return success(sceneService.selectSceneOptions(new CostScene()));
    }

    @GetMapping("/{appId}")
    public AjaxResult detail(@PathVariable Long appId) {
        return success(openAppService.selectOpenAppById(appId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostOpenApp app) {
        app.setCreateBy(operator());
        app.setUpdateBy(operator());
        if (!openAppService.checkOpenAppCodeUnique(app)) {
            return error("开放应用编码已存在");
        }
        return success(openAppService.insertOpenApp(app));
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostOpenApp app) {
        app.setUpdateBy(operator());
        return openAppService.checkOpenAppCodeUnique(app)
                ? toAjax(openAppService.updateOpenApp(app))
                : error("开放应用编码已存在");
    }

    @PutMapping("/resetSecret/{appId}")
    public AjaxResult resetSecret(@PathVariable Long appId) {
        return success(openAppService.resetOpenAppSecret(appId, operator()));
    }

    @DeleteMapping("/{appIds}")
    public AjaxResult remove(@PathVariable Long[] appIds) {
        return toAjax(openAppService.deleteOpenAppByIds(appIds));
    }
}
