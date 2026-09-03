package com.ruoyi.system.domain.vo;

import com.ruoyi.common.annotation.Excel;
import lombok.Data;

/**
 * Variable import validation issue.
 *
 * @author HwFan
 */
@Data
public class CostVariableImportIssueVo {
    @Excel(name = "Excel行号")
    private Integer rowNum;

    @Excel(name = "场景编码")
    private String sceneCode;

    @Excel(name = "变量编码")
    private String variableCode;

    @Excel(name = "变量名称")
    private String variableName;

    @Excel(name = "字段编码")
    private String fieldName;

    @Excel(name = "字段名称")
    private String fieldLabel;

    @Excel(name = "原始值")
    private String rawValue;

    @Excel(name = "校验问题")
    private String message;
}
