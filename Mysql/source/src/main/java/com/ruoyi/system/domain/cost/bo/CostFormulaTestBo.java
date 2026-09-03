package com.ruoyi.system.domain.cost.bo;

import lombok.Data;

@Data
public class CostFormulaTestBo {
    private Long formulaId;

    private Long sceneId;

    private String formulaCode;

    private String formulaExpr;

    private String inputJson;

    private String namespaceScope;
}
