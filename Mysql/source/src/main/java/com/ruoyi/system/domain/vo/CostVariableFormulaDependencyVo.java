package com.ruoyi.system.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式变量输入依赖节点。
 */
@Data
public class CostVariableFormulaDependencyVo {
    private Long variableId;

    private String variableCode;

    private String variableName;

    private String sourceType;

    private String status;

    private String formulaCode;

    private String formulaName;

    private Boolean missing = false;

    private Boolean circular = false;

    private List<CostVariableFormulaDependencyVo> children = new ArrayList<>();
}
