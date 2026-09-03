package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;

import java.util.List;

public interface PublishValidationRule {
    void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items);
}
