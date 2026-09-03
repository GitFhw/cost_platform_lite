package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.lite.web.CostLiteTableSupport;
import com.ruoyi.system.domain.cost.CostFeeItem;
import com.ruoyi.system.service.cost.ICostFeeService;
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
 * 轻量费目维护接口。
 */
@RestController
@RequestMapping("/cost/fee")
public class CostLiteFeeController extends CostLiteControllerSupport {
    private final ICostFeeService feeService;

    public CostLiteFeeController(ICostFeeService feeService, CostLiteProperties properties) {
        super(properties);
        this.feeService = feeService;
    }

    @GetMapping("/list")
    public TableDataInfo list(CostFeeItem query,
                              @RequestParam(value = "pageNum", required = false) Integer pageNum,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return CostLiteTableSupport.table(feeService.selectFeeList(query), pageNum, pageSize, properties);
    }

    @GetMapping("/stats")
    public AjaxResult stats(CostFeeItem query) {
        return success(feeService.selectFeeStats(query));
    }

    @GetMapping("/governance/{feeId}")
    public AjaxResult governance(@PathVariable Long feeId) {
        return success(feeService.selectFeeGovernanceCheck(feeId));
    }

    @GetMapping("/optionselect")
    public AjaxResult options(CostFeeItem query) {
        return success(feeService.selectFeeOptions(query));
    }

    @GetMapping("/{feeId}")
    public AjaxResult detail(@PathVariable Long feeId) {
        return success(feeService.selectFeeById(feeId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostFeeItem fee) {
        fee.setCreateBy(operator());
        fee.setUpdateBy(operator());
        return feeService.checkFeeCodeUnique(fee)
                ? toAjax(feeService.insertFee(fee))
                : error("同一场景下费目编码已存在");
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostFeeItem fee) {
        fee.setUpdateBy(operator());
        return feeService.checkFeeCodeUnique(fee)
                ? toAjax(feeService.updateFee(fee))
                : error("同一场景下费目编码已存在");
    }

    @PutMapping("/disable/{feeIds}")
    public AjaxResult disable(@PathVariable Long[] feeIds) {
        return toAjax(feeService.disableFeeByIds(feeIds));
    }

    @DeleteMapping("/{feeIds}")
    public AjaxResult remove(@PathVariable Long[] feeIds) {
        return toAjax(feeService.deleteFeeByIds(feeIds));
    }
}
