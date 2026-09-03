package com.ruoyi.system.service.cost.publish.validation;

import com.ruoyi.system.domain.vo.CostPublishCheckItemVo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Ordered publish precheck chain.
 */
@Service
public class PublishValidationChain {
    private final List<PublishValidationRule> rules;

    public PublishValidationChain(BasicPublishValidationRule basicRule,
                                  FormulaPublishValidationRule formulaRule,
                                  RuleReferencePublishValidationRule referenceRule,
                                  PublishablePublishValidationRule publishableRule,
                                  VersionDiffPublishValidationRule versionDiffRule) {
        this.rules = Arrays.asList(basicRule, formulaRule, referenceRule, publishableRule, versionDiffRule);
    }

    public void validate(PublishValidationContext context, List<CostPublishCheckItemVo> items) {
        for (PublishValidationRule rule : rules) {
            rule.validate(context, items);
        }
    }
}
