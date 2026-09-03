package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublishablePublishValidationRule implements PublishValidationRule {

    @Override
    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        long blockingCount = items.stream().filter(item -> "BLOCK".equals(item.getLevel())).count();
        long warningCount = items.stream().filter(item -> "WARN".equals(item.getLevel())).count();
        if (blockingCount > 0) {
            items.add(PublishValidationSupport.checkItem("BLOCK", "PUBLISH_BLOCKED", "可发布性判断",
                    String.format("当前发布校验存在%1$d项阻断，请先处理后再发布。", blockingCount)));
            return;
        }
        if (warningCount > 0) {
            items.add(PublishValidationSupport.checkItem("WARN", "PUBLISH_WARN", "可发布性判断",
                    String.format("当前发布校验存在%1$d项告警，可继续发布但建议先确认。", warningCount)));
            return;
        }
        items.add(PublishValidationSupport.checkItem("PASS", "PUBLISHABLE", "可发布性判断", "当前发布校验通过，可发布版本。"));
    }
}
