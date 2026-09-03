package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式实验室治理检查结果。
 *
 * @author HwFan
 */
@Data
public class CostFormulaGovernanceCheckVo {
    private Long formulaId;
    private Long sceneId;
    private String formulaCode;
    private String formulaName;
    private Long variableRefCount;
    private Long ruleRefCount;
    private Long publishedVersionCount;
    private Boolean canDelete;
    private Boolean canDisable;
    private String removeBlockingReason;
    private String disableBlockingReason;
    private List<CostGovernanceImpactVo> impactItems = new ArrayList<>();
}
