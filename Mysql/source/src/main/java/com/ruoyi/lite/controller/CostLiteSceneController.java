package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostScene;
import com.ruoyi.system.domain.cost.bo.CostSceneCopyBo;
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
 * 轻量场景维护接口，路径与母体保持一致。
 */
@RestController
@RequestMapping("/cost/scene")
public class CostLiteSceneController extends CostLiteControllerSupport {
    private final ICostSceneService sceneService;

    public CostLiteSceneController(ICostSceneService sceneService, CostLiteProperties properties) {
        super(properties);
        this.sceneService = sceneService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostScene query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(sceneService.selectSceneList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostScene query) {
        return success(sceneService.selectSceneStats(query));
    }

    @GetMapping("/governance/{sceneId}")
    public AjaxResult governance(@PathVariable Long sceneId) {
        return success(sceneService.selectSceneGovernanceCheck(sceneId));
    }

    @GetMapping("/optionselect")
    public AjaxResult options(CostScene query) {
        return success(sceneService.selectSceneOptions(query));
    }

    @GetMapping("/{sceneId}")
    public AjaxResult detail(@PathVariable Long sceneId) {
        return success(sceneService.selectSceneById(sceneId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostScene scene) {
        scene.setCreateBy(operator());
        scene.setUpdateBy(operator());
        return sceneService.checkSceneCodeUnique(scene)
                ? toAjax(sceneService.insertScene(scene))
                : error("场景编码已存在");
    }

    @PostMapping("/copy")
    public AjaxResult copy(@Valid @RequestBody CostSceneCopyBo request) {
        return success(sceneService.copyScene(request));
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostScene scene) {
        scene.setUpdateBy(operator());
        return sceneService.checkSceneCodeUnique(scene)
                ? toAjax(sceneService.updateScene(scene))
                : error("场景编码已存在");
    }

    @DeleteMapping("/{sceneIds}")
    public AjaxResult remove(@PathVariable Long[] sceneIds) {
        return toAjax(sceneService.deleteSceneByIds(sceneIds));
    }
}
