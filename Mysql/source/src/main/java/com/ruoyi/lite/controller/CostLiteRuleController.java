package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostRule;
import com.ruoyi.system.domain.cost.bo.CostRuleCopyBo;
import com.ruoyi.system.domain.cost.bo.CostRuleSaveBo;
import com.ruoyi.system.domain.cost.bo.CostRuleTierPreviewBo;
import com.ruoyi.system.service.cost.ICostRuleService;
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
 * 轻量规则维护接口。
 */
@RestController
@RequestMapping("/cost/rule")
public class CostLiteRuleController extends CostLiteControllerSupport {
    private final ICostRuleService ruleService;

    public CostLiteRuleController(ICostRuleService ruleService, CostLiteProperties properties) {
        super(properties);
        this.ruleService = ruleService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostRule query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(ruleService.selectRuleList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostRule query) {
        return success(ruleService.selectRuleStats(query));
    }

    @GetMapping("/governance/{ruleId}")
    public AjaxResult governance(@PathVariable Long ruleId) {
        return success(ruleService.selectRuleGovernanceCheck(ruleId));
    }

    @GetMapping("/{ruleId}")
    public AjaxResult detail(@PathVariable Long ruleId) {
        return success(ruleService.selectRuleDetail(ruleId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostRuleSaveBo rule) {
        return ruleService.checkRuleCodeUnique(rule)
                ? toAjax(ruleService.insertRule(rule))
                : error("同一场景下规则编码已存在");
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostRuleSaveBo rule) {
        return ruleService.checkRuleCodeUnique(rule)
                ? toAjax(ruleService.updateRule(rule))
                : error("同一场景下规则编码已存在");
    }

    @PostMapping("/copy")
    public AjaxResult copy(@Valid @RequestBody CostRuleCopyBo request) {
        return toAjax(ruleService.copyRule(request));
    }

    @PostMapping("/tierPreview")
    public AjaxResult tierPreview(@Valid @RequestBody CostRuleTierPreviewBo request) {
        return success(ruleService.previewTierHit(request));
    }

    @PostMapping("/conflictPreview")
    public AjaxResult conflictPreview(@Valid @RequestBody CostRuleSaveBo request) {
        return success(ruleService.previewRuleConflicts(request));
    }

    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds) {
        return toAjax(ruleService.deleteRuleByIds(ruleIds));
    }
}
