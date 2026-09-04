package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostFormula;
import com.ruoyi.system.domain.cost.bo.CostFormulaTestBo;
import com.ruoyi.system.service.cost.ICostFormulaService;
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

/**
 * 轻量公式维护接口。
 */
@RestController
@RequestMapping("/cost/formula")
public class CostLiteFormulaController extends CostLiteControllerSupport {
    private final ICostFormulaService formulaService;

    public CostLiteFormulaController(ICostFormulaService formulaService, CostLiteProperties properties) {
        super(properties);
        this.formulaService = formulaService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostFormula query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(formulaService.selectFormulaList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostFormula query) {
        return success(formulaService.selectFormulaStats(query));
    }

    @GetMapping("/governance/{formulaId}")
    public AjaxResult governance(@PathVariable Long formulaId) {
        return success(formulaService.selectFormulaGovernanceCheck(formulaId));
    }

    @GetMapping("/optionselect")
    public AjaxResult options(CostFormula query) {
        return success(formulaService.selectFormulaOptions(query));
    }

    @GetMapping("/templateOptions")
    public AjaxResult templateOptions(CostFormula query) {
        return success(formulaService.selectTemplateOptions(query));
    }

    @GetMapping("/{formulaId}")
    public AjaxResult detail(@PathVariable Long formulaId) {
        return success(formulaService.selectFormulaById(formulaId));
    }

    @GetMapping("/versions/{formulaId}")
    public AjaxResult versions(@PathVariable Long formulaId) {
        return success(formulaService.selectFormulaVersionList(formulaId));
    }

    @GetMapping("/version/{versionId}")
    public AjaxResult version(@PathVariable Long versionId) {
        return success(formulaService.selectFormulaVersionDetail(versionId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostFormula formula) {
        formula.setCreateBy(operator());
        return formulaService.checkFormulaCodeUnique(formula)
                ? toAjax(formulaService.insertFormula(formula))
                : error("同一场景下公式编码已存在");
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostFormula formula) {
        formula.setUpdateBy(operator());
        return formulaService.checkFormulaCodeUnique(formula)
                ? toAjax(formulaService.updateFormula(formula))
                : error("同一场景下公式编码已存在");
    }

    @PutMapping("/version/rollback/{versionId}")
    public AjaxResult rollback(@PathVariable Long versionId) {
        return toAjax(formulaService.rollbackFormulaVersion(versionId, operator()));
    }

    @PostMapping("/test")
    public AjaxResult test(@RequestBody CostFormulaTestBo request) {
        return success(formulaService.testFormula(request, operator()));
    }

    @DeleteMapping("/{formulaIds}")
    public AjaxResult remove(@PathVariable Long[] formulaIds) {
        return toAjax(formulaService.deleteFormulaByIds(formulaIds));
    }
}
