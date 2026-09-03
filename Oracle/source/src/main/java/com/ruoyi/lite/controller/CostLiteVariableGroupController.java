package com.ruoyi.lite.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lite.config.CostLiteProperties;
import com.ruoyi.lite.web.CostLiteControllerSupport;
import com.ruoyi.system.domain.cost.CostVariableGroup;
import com.ruoyi.system.service.cost.ICostVariableGroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 要素分组维护接口。
 */
@RestController
@RequestMapping("/cost/variable/group")
public class CostLiteVariableGroupController extends CostLiteControllerSupport {
    private final ICostVariableGroupService groupService;

    public CostLiteVariableGroupController(ICostVariableGroupService groupService, CostLiteProperties properties) {
        super(properties);
        this.groupService = groupService;
    }

    @GetMapping("/list")
    public AjaxResult list(CostVariableGroup query) {
        return success(groupService.selectVariableGroupList(query));
    }

    @GetMapping("/optionselect")
    public AjaxResult options(CostVariableGroup query) {
        return success(groupService.selectVariableGroupOptions(query));
    }

    @GetMapping("/{groupId}")
    public AjaxResult detail(@PathVariable Long groupId) {
        return success(groupService.selectVariableGroupById(groupId));
    }

    @PostMapping
    public AjaxResult add(@Valid @RequestBody CostVariableGroup group) {
        return groupService.checkGroupCodeUnique(group)
                ? toAjax(groupService.insertVariableGroup(group))
                : error("同一场景下要素分组编码已存在");
    }

    @PutMapping
    public AjaxResult edit(@Valid @RequestBody CostVariableGroup group) {
        return groupService.checkGroupCodeUnique(group)
                ? toAjax(groupService.updateVariableGroup(group))
                : error("同一场景下要素分组编码已存在");
    }

    @DeleteMapping("/{groupIds}")
    public AjaxResult remove(@PathVariable Long[] groupIds) {
        return toAjax(groupService.deleteVariableGroupByIds(groupIds));
    }
}
