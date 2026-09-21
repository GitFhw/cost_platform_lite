package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostPublishVersion;
import com.ruoyi.system.domain.cost.bo.CostPublishCreateBo;
import com.ruoyi.system.service.cost.ICostPublishService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轻量版本发布接口。
 */
@RestController
@RequestMapping("/cost/publish")
public class CostLitePublishController extends CostLiteControllerSupport {
    private final ICostPublishService publishService;

    public CostLitePublishController(ICostPublishService publishService, CostLiteProperties properties) {
        super(properties);
        this.publishService = publishService;
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostPublishVersion query) {
        return success(publishService.selectPublishStats(query));
    }

    @GetMapping("/list")
    public TableDataInfo list(CostPublishVersion query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(publishService.selectPublishVersionList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/precheck/{sceneId}")
    public AjaxResult precheck(@PathVariable Long sceneId) {
        return success(publishService.selectPublishPrecheck(sceneId));
    }

    @GetMapping("/diff")
    public AjaxResult diff(@RequestParam Long fromVersionId,
                           @RequestParam Long toVersionId,
                           @RequestParam(required = false) String feeCode) {
        return success(publishService.selectPublishDiff(fromVersionId, toVersionId, feeCode));
    }

    @GetMapping("/{versionId}")
    public AjaxResult detail(@PathVariable Long versionId,
                             @RequestParam(required = false) String feeCode) {
        return success(publishService.selectPublishVersionDetail(versionId, feeCode));
    }

    @PostMapping
    public AjaxResult publish(@Valid @RequestBody CostPublishCreateBo request) {
        return toAjax(publishService.publishScene(request));
    }

    @PutMapping("/activate/{versionId}")
    public AjaxResult activate(@PathVariable Long versionId) {
        return toAjax(publishService.activateVersion(versionId));
    }

    @PutMapping("/rollback/{versionId}")
    public AjaxResult rollback(@PathVariable Long versionId) {
        return toAjax(publishService.rollbackVersion(versionId));
    }
}
